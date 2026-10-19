package top.x0a.strapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import top.x0a.strapper.JsonReader.JsonReadException;
import top.x0a.strapper.Types.TypeDef;

/**
 * WEB API 参数契约校验器 —— libstrapper 的 Java 实现。
 *
 * <p>用一段 JSON 形态的"格式描述"去校验另一段 JSON 形态的"参数数据"，产出归一化后的取值与问题清单。
 * 格式描述的语法见 spec 的 valueFormat.md 与 dataStruct.md。
 *
 * <h2>最简用法</h2>
 * <pre>{@code
 * String format = """
 *     {
 *       "username":"*string:32//用户名",
 *       "age":"int[0,150]#0//年龄",
 *       "gender":"string{男,女}//性别可以不填"
 *     }""";
 *
 * StrapResult result = new Strapper().strap(format, requestBody);
 * if (!result.ok()) {
 *     return fail(result.report());
 * }
 * String username = (String) result.asMap().get("username");
 * }</pre>
 *
 * <h2>扩展自己领域的类型</h2>
 * <p>扩展类型就是一张「名 : 正则」的明文表，内置的 mobile、idcard 等只是预先写好的几行，
 * 业务领域的类型走完全相同的入口：
 * <pre>{@code
 * Strapper strapper = new Strapper()
 *         // 共享电动车领域：车辆编号 9 位数字，前 4 位是生产批次
 *         .addType("int.vehicleNo", "^20\\d{2}\\d{5}$")
 *         // 也可以整张表放在接口文档旁边一起维护
 *         .loadTypes(Path.of("conf/types.txt"));
 * // 格式描述里写 "*vehicleNo//车辆编号" 或 "*int.vehicleNo//车辆编号"
 * }</pre>
 *
 * <h2>换掉内置的 JSON 解析</h2>
 * <p>项目里已经在用 Jackson / Gson / fastjson2 时，不必再驮一套解析实现：
 * <pre>{@code
 * strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));
 * }</pre>
 * 具体约定和几个要当心的差异见 {@link JsonReader}。
 *
 * <h2>线程安全</h2>
 * <p>配置完成后 {@link #strap} 不改变实例状态（每次调用的状态都在局部），可以在多线程间共享。
 *
 * <p>但配置字段是非 {@code final} 的，{@link Types} 内部也是可变的 map，所以配置必须发生在
 * "发布给其他线程之前"：
 * <pre>{@code
 * // 安全：静态初始化有 happens-before 保证
 * static final Strapper STRAPPER = new Strapper().addType(...).disableType("text");
 *
 * // 危险：已经在跑请求了才改配置，别的线程可能看到半配好的状态
 * STRAPPER.disableType("text");
 * }</pre>
 */
public final class Strapper {

    /** 格式描述里出现未定义类型时的处理策略。 */
    public enum UnknownTypePolicy {
        /** 报 {@link ErrorCode#FORMAT_UNKNOWN_KEYTYPE_ERROR}，让类型名的笔误在开发期就暴露。默认值。 */
        ERROR,
        /** 按 {@code text} 放行并记一条告警。PHP 版是这个行为，迁移期可用。 */
        PASS_THROUGH
    }

    /**
     * 判定 {@code !} 标记的参数在当前上下文下到底必不必填。
     *
     * <p>spec 说这类条件"需要在具体情境下具体描述"，所以判断权在调用方。
     */
    @FunctionalInterface
    public interface ConditionalResolver {
        /**
         * @param path     该参数的完整路径，如 {@code $.data.invoiceTitle}
         * @param keyName  该参数的 key 名
         * @param siblings 同一层已知的原始数据（只读），可据此判断
         */
        boolean isRequired(String path, String keyName, Map<String, Object> siblings);
    }

    private static final ConditionalResolver ALWAYS_OPTIONAL = (path, key, siblings) -> false;

    private Types types = Types.standard();
    private JsonReader jsonReader = JsonReader.builtin();
    private ConditionalResolver conditionalResolver = ALWAYS_OPTIONAL;
    private UnknownTypePolicy unknownTypePolicy = UnknownTypePolicy.ERROR;

    // =================================================================
    // 配置
    // =================================================================

    /**
     * 换用另一个 JSON 解析器。默认是内置的零依赖实现。
     *
     * <p>项目里已经在用 Jackson / Gson / fastjson2 时，包一层传进来就不必驮两套解析实现：
     * <pre>{@code
     * strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));
     * }</pre>
     * 换之前请先读 {@link JsonReader} 上「换之前要知道的三件事」，尤其是 Gson 的数字精度问题。
     */
    public Strapper jsonReader(JsonReader reader) {
        this.jsonReader = Objects.requireNonNull(reader, "reader");
        return this;
    }

    /** 当前使用的 JSON 解析器。 */
    public JsonReader jsonReader() {
        return jsonReader;
    }

    /** 换用另一张类型表。 */
    public Strapper types(Types types) {
        this.types = Objects.requireNonNull(types, "types");
        return this;
    }

    /** 当前的类型表，可直接在上面继续 {@code add}。 */
    public Types types() {
        return types;
    }

    /**
     * 加一条扩展类型。
     *
     * @param name  点分全名，形如 {@code int.vehicleNo}；点号前必须是基础类型名
     * @param regex 该类型的正则
     */
    public Strapper addType(String name, String regex) {
        types.add(name, regex);
        return this;
    }

    /** 批量加扩展类型，键为全名，值为正则。 */
    public Strapper addTypes(Map<String, String> entries) {
        types.addAll(entries);
        return this;
    }

    /** 从明文读入一张扩展类型表，格式见 {@link Types#load(String)}。 */
    public Strapper loadTypes(String text) {
        types.load(text);
        return this;
    }

    /** 从文件读入一张扩展类型表，UTF-8 编码。 */
    public Strapper loadTypes(Path file) throws IOException {
        types.load(file);
        return this;
    }

    /**
     * 禁用一个类型，之后格式描述里写到它就会报错。
     *
     * <p>禁用基础类型会连带禁用从它派生的扩展类型。典型用法是
     * {@code disableType("text")}：不接受任何可能带制表符、换行符的参数，收紧攻击面。
     */
    public Strapper disableType(String name) {
        types.disable(name);
        return this;
    }

    /** 解除禁用。 */
    public Strapper enableType(String name) {
        types.enable(name);
        return this;
    }

    /** 设置 {@code !} 条件必填的判定逻辑。 */
    public Strapper conditionalResolver(ConditionalResolver resolver) {
        this.conditionalResolver = resolver == null ? ALWAYS_OPTIONAL : resolver;
        return this;
    }

    /** 设置未定义类型的处理策略。 */
    public Strapper unknownTypePolicy(UnknownTypePolicy policy) {
        this.unknownTypePolicy = Objects.requireNonNull(policy, "policy");
        return this;
    }

    // =================================================================
    // 对外入口
    // =================================================================

    /**
     * 只检查格式描述本身写得对不对，不需要数据。适合在接口文档的 CI 里跑。
     *
     * @return {@link StrapResult#value()} 为解析后的格式树
     */
    public StrapResult checkFormat(String format) {
        Run run = new Run();
        Object tree = readFormat(format, run);
        if (tree == null) {
            return new StrapResult(false, null, run.errors, run.warnings);
        }
        checkFormatNode(tree, "$", run);
        return new StrapResult(run.errors.isEmpty(), tree, run.errors, run.warnings);
    }

    /**
     * 用格式描述校验一段 JSON 数据。
     *
     * @param format 格式描述；空串按 {@code "{}"} 处理（dataStruct 特殊情况一）
     * @param json   待校验的 JSON 文本
     */
    public StrapResult strap(String format, String json) {
        Run run = new Run();

        Object formatTree = readFormat(format, run);
        if (formatTree == null) {
            return new StrapResult(false, null, run.errors, run.warnings);
        }

        // 格式描述自身有错时不再往下走：拿一份错的契约去校验数据，报出来的问题只会误导人
        checkFormatNode(formatTree, "$", run);
        if (!run.errors.isEmpty()) {
            return new StrapResult(false, null, run.errors, run.warnings);
        }

        Object data;
        try {
            data = jsonReader.read(json == null || json.isBlank() ? "{}" : json);
        } catch (JsonReadException e) {
            run.error("$", readerErrorCode(ErrorCode.DATA_JSON_STRUCT_ERROR,
                    ErrorCode.DATA_JSON_READER_ERROR), e.getMessage());
            return new StrapResult(false, null, run.errors, run.warnings);
        }

        Object value = walk(formatTree, data, "$", null, run);
        return new StrapResult(run.errors.isEmpty(), value, run.errors, run.warnings);
    }

    /** 解析格式描述文本；失败时把问题记进 run 并返回 null。 */
    private Object readFormat(String format, Run run) {
        String text = (format == null || format.isBlank()) ? "{}" : format;
        Object tree;
        try {
            tree = jsonReader.read(text);
        } catch (JsonReadException e) {
            run.error("$", readerErrorCode(ErrorCode.FORMAT_JSON_STRUCT_ERROR,
                    ErrorCode.FORMAT_JSON_READER_ERROR), e.getMessage());
            return null;
        }
        if (!(tree instanceof Map) && !(tree instanceof List)) {
            run.error("$", ErrorCode.FORMAT_SYNTAX_ERROR,
                    "格式描述的顶层必须是对象或数组，不接受纯量: " + text);
            return null;
        }
        return tree;
    }

    // =================================================================
    // 格式描述自检
    // =================================================================

    private void checkFormatNode(Object node, String path, Run run) {
        switch (node) {
            case Map<?, ?> map -> checkFormatObject(map, path, run);
            case List<?> list -> checkFormatArray(list, path, run);
            case String s -> checkFormatString(s, path, run);
            case null -> run.error(path, ErrorCode.FORMAT_SYNTAX_ERROR, "格式描述里不能出现 null");
            default -> run.error(path, ErrorCode.FORMAT_SYNTAX_ERROR,
                    "格式描述只能由对象、数组和格式串构成，这里是 " + node.getClass().getSimpleName());
        }
    }

    private void checkFormatObject(Map<?, ?> map, String path, Run run) {
        boolean seenMoreKeys = false;
        String seenWildcard = null;
        Set<String> names = new LinkedHashSet<>();

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String rawKey = String.valueOf(entry.getKey());
            KeyFormat key;
            try {
                key = KeyFormat.parse(rawKey, types);
            } catch (FormatException e) {
                run.error(child(path, rawKey), e.code(), e.detail());
                continue;
            }

            switch (key.kind()) {
                case MORE_KEYS -> {
                    if (seenMoreKeys) {
                        run.error(path, ErrorCode.FORMAT_SYNTAX_ERROR, "同一层出现了多个 \"...\"");
                    }
                    seenMoreKeys = true;
                    // "..." 的值可以是字典、数组或一个值格式；前两者不再往下检查（spec 明确不做进一步检查）
                    if (entry.getValue() instanceof String s) {
                        checkFormatString(s, child(path, rawKey), run);
                    }
                }
                case WILDCARD -> {
                    if (seenWildcard != null) {
                        run.error(path, ErrorCode.FORMAT_SYNTAX_ERROR,
                                "同一层出现了多个通配 key: " + seenWildcard + " 与 " + rawKey);
                    }
                    seenWildcard = rawKey;
                    if (entry.getValue() instanceof String s) {
                        checkFormatString(s, child(path, rawKey), run);
                    } else {
                        run.error(child(path, rawKey), ErrorCode.FORMAT_SYNTAX_ERROR,
                                "通配 key 的值必须是一个值格式串");
                    }
                }
                case NORMAL -> {
                    if (!names.add(key.name())) {
                        run.error(path, ErrorCode.FORMAT_SYNTAX_ERROR, "key 重复: " + key.name());
                    }
                    if (key.hasItemsRange() && !(entry.getValue() instanceof List)) {
                        run.error(child(path, key.name()), ErrorCode.FORMAT_KEY_SYNTAX_ERROR,
                                "key 上写了数组长度约定，但值不是数组");
                    }
                    checkFormatNode(entry.getValue(), child(path, key.name()), run);
                }
                default -> throw new IllegalStateException("unreachable: " + key.kind());
            }
        }
    }

    private void checkFormatArray(List<?> list, String path, Run run) {
        if (list.isEmpty()) {
            run.error(path, ErrorCode.FORMAT_NOT_SUPPORT_NOFORMAT_ARRAY,
                    "数组里必须写出元素的格式");
            return;
        }
        if (list.size() > 1) {
            run.error(path, ErrorCode.FORMAT_NOT_SUPPORT_MULTIFORMAT_ARRAY,
                    "数组里写了 " + list.size() + " 个格式，只允许 1 个");
            return;
        }
        checkFormatNode(list.get(0), path + "[*]", run);
    }

    private void checkFormatString(String source, String path, Run run) {
        ValueFormat vf;
        try {
            vf = ValueFormat.parse(source);
        } catch (FormatException e) {
            run.error(path, e.code(), e.detail());
            return;
        }
        if (types.resolve(vf.typeName()) != null) {
            return;
        }
        // 被禁用是本项目的明确决定，和"类型名打错了"不是一回事，也不该被 PASS_THROUGH 放过去
        if (types.isDisabled(vf.typeName())) {
            run.error(path, ErrorCode.FORMAT_TYPE_DISABLED, types.resolveProblem(vf.typeName()));
            return;
        }
        if (unknownTypePolicy == UnknownTypePolicy.ERROR) {
            run.error(path, ErrorCode.FORMAT_UNKNOWN_KEYTYPE_ERROR,
                    types.resolveProblem(vf.typeName()));
        } else {
            run.warn(path, ErrorCode.FORMAT_UNKNOWN_KEYTYPE_ERROR,
                    types.resolveProblem(vf.typeName()) + "，按 text 放行");
        }
    }

    // =================================================================
    // 数据校验
    // =================================================================

    /** 按格式节点的种类分发。{@code key} 仅在值为数组时用于取长度约定。 */
    private Object walk(Object formatNode, Object data, String path, KeyFormat key, Run run) {
        if (formatNode instanceof Map<?, ?> fmt) {
            if (data instanceof Map<?, ?> obj) {
                return walkObject(fmt, obj, path, run);
            }
            run.error(path, ErrorCode.DATA_NOT_MATCHED, "格式要求是对象，实际是 " + typeOf(data));
            return null;
        }
        if (formatNode instanceof List<?> fmt) {
            if (data instanceof List<?> arr) {
                return walkArray(fmt, arr, path, key, run);
            }
            run.error(path, ErrorCode.DATA_NOT_MATCHED, "格式要求是数组，实际是 " + typeOf(data));
            return null;
        }
        if (formatNode instanceof String fmt) {
            if (data instanceof Map || data instanceof List) {
                run.error(path, ErrorCode.DATA_NOT_MATCHED, "格式要求是单个值，实际是 " + typeOf(data));
                return null;
            }
            return checkValue(fmt, data, path, run);
        }
        run.error(path, ErrorCode.FORMAT_SYNTAX_ERROR, "无法识别的格式节点");
        return null;
    }

    private Map<String, Object> walkObject(Map<?, ?> format, Map<?, ?> data, String path, Run run) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> siblings = asStringKeyMap(data);
        Set<String> consumed = new LinkedHashSet<>();

        Object moreKeysSpec = null;
        boolean moreKeys = false;
        KeyFormat wildcardKey = null;
        String wildcardValueFormat = null;

        for (Map.Entry<?, ?> entry : format.entrySet()) {
            String rawKey = String.valueOf(entry.getKey());
            Object fmtValue = entry.getValue();
            KeyFormat key = KeyFormat.parse(rawKey, types);   // 已在自检阶段验证过，这里不会抛

            if (key.kind() == KeyFormat.Kind.MORE_KEYS) {
                moreKeys = true;
                moreKeysSpec = fmtValue;
                continue;
            }
            if (key.kind() == KeyFormat.Kind.WILDCARD) {
                wildcardKey = key;
                wildcardValueFormat = (String) fmtValue;
                continue;
            }

            String name = key.name();
            String childPath = child(path, name);
            consumed.add(name);

            // JSON 里显式写 null 与整个 key 缺失同等对待（与 PHP 版的 isset 语义一致）
            boolean present = data.containsKey(name) && data.get(name) != null;
            if (!present) {
                handleMissing(key, fmtValue, name, childPath, siblings, result, run);
                continue;
            }

            Object value = walk(fmtValue, data.get(name), childPath, key, run);
            if (value != null) {
                result.put(name, value);
            }
        }

        // 格式里没列出的 key：通配 key 优先，其次 "..."，都没有则丢弃并告警
        for (Map.Entry<String, Object> entry : siblings.entrySet()) {
            String name = entry.getKey();
            if (consumed.contains(name)) {
                continue;
            }
            String childPath = child(path, name);
            if (wildcardKey != null) {
                if (!checkWildcardKeyName(wildcardKey, name, childPath, run)) {
                    continue;
                }
                Object value = walk(wildcardValueFormat, entry.getValue(), childPath, null, run);
                if (value != null) {
                    result.put(name, value);
                }
            } else if (moreKeys) {
                if (moreKeysSpec instanceof String spec) {
                    Object value = walk(spec, entry.getValue(), childPath, null, run);
                    if (value != null) {
                        result.put(name, value);
                    }
                } else {
                    // spec: "..." 的值为字典或数组时不对其值做进一步检查，原样收下
                    result.put(name, entry.getValue());
                }
            } else {
                run.warn(childPath, ErrorCode.DATA_UNKNOWN_KEY_ERROR, "格式中未定义，已丢弃");
            }
        }

        return result;
    }

    /** 缺失的 key：按必填性报错，或用默认值补上。 */
    private void handleMissing(KeyFormat key, Object fmtValue, String name, String childPath,
                               Map<String, Object> siblings, Map<String, Object> result, Run run) {
        Requirement requirement = key.requirement();
        ValueFormat vf = null;
        if (fmtValue instanceof String s) {
            vf = ValueFormat.parse(s);
            requirement = Requirement.strongest(requirement, vf.requirement());
        }

        if (requirement == Requirement.CONDITIONAL) {
            requirement = conditionalResolver.isRequired(childPath, name, siblings)
                    ? Requirement.REQUIRED
                    : Requirement.OPTIONAL;
        }

        if (requirement == Requirement.REQUIRED) {
            run.error(childPath, ErrorCode.DATA_KEY_NEED_EXIST, "必填参数 " + name + " 未提供");
            return;
        }

        // 选填且格式里写了默认值：直接补上，让调用方拿到一份字段齐全的取值
        if (vf != null && vf.hasDefault()) {
            defaultValue(vf, childPath, run).ifPresent(v -> result.put(name, v));
        }
    }

    private boolean checkWildcardKeyName(KeyFormat wildcard, String name, String path, Run run) {
        TypeDef def = types.resolve(wildcard.keyType());
        if (def == null) {
            return true;
        }
        String reason = Types.match(def, name);
        if (reason != null) {
            run.error(path, ErrorCode.DATA_NOT_MATCHED, "key 名 " + reason);
            return false;
        }
        return true;
    }

    private List<Object> walkArray(List<?> format, List<?> data, String path, KeyFormat key, Run run) {
        // 数组内元素格式唯一，自检阶段已保证
        Object elementFormat = format.get(0);

        if (key != null && key.hasItemsRange() && !key.acceptsSize(data.size())) {
            run.error(path, ErrorCode.DATA_ARRAY_LENGTH_ERROR,
                    "长度约定为 " + key.itemsRangeText() + "，实际 " + data.size() + " 个");
        }

        List<Object> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            result.add(walk(elementFormat, data.get(i), path + "[" + i + "]", null, run));
        }
        return result;
    }

    // =================================================================
    // 单个值的校验
    // =================================================================

    private Object checkValue(String formatString, Object data, String path, Run run) {
        ValueFormat vf = ValueFormat.parse(formatString);
        TypeDef def = types.resolve(vf.typeName());
        String raw = Values.raw(data);

        if (def == null) {
            // 自检阶段已按策略处理过，这里是 PASS_THROUGH：按 text 收下
            return raw;
        }

        String failure = evaluate(vf, def, raw, path, run);
        if (failure == null) {
            return coerceQuietly(def, raw);
        }

        if (vf.hasDefault()) {
            Optional<Object> fallback = defaultValue(vf, path, run);
            if (fallback.isPresent()) {
                run.warn(path, ErrorCode.DATA_USE_DEFAULT_VALUE,
                        failure + "，已改用默认值 " + vf.defaultValue());
                return fallback.get();
            }
            return null;
        }

        run.error(path, failureCode(failure), failure);
        return null;
    }

    /**
     * 依次做长度、格式、范围三项检查。
     *
     * @return 通过返回 {@code null}，否则返回失败原因
     */
    private String evaluate(ValueFormat vf, TypeDef def, String raw, String path, Run run) {
        if (vf.length() != null && raw.length() > vf.length()) {
            return "长度 " + raw.length() + " 超过约定的 " + vf.length();
        }

        Object value;
        try {
            // 先过根类型：派生类型的校验器要拿到已转换的值，得先有根类型的取值
            String rootFailure = Types.matchBase(def, raw);
            if (rootFailure != null) {
                return rootFailure;
            }
            value = Values.coerce(def.kind(), raw);
            String typeFailure = Types.match(def, raw);
            if (typeFailure != null) {
                return typeFailure;
            }
        } catch (Values.ValueException e) {
            return e.getMessage();
        }

        if (vf.range() == null) {
            return null;
        }
        try {
            return rangeCheck(vf.range(), def, raw, value);
        } catch (Values.ValueException e) {
            return e.getMessage();
        } catch (FormatException e) {
            run.error(path, e.code(), e.detail());
            return null;
        }
    }

    private static String rangeCheck(ValueFormat.Range range, TypeDef def, String raw, Object value) {
        if (range.enumeration()) {
            if (Values.inEnum(def.kind(), raw, value, range.members())) {
                return null;
            }
            return "不在枚举 " + range + " 之内";
        }
        if (!range.lowerUnbounded()) {
            int cmp = Values.compareToBound(def.kind(), raw, value, range.min());
            if (cmp < 0 || (cmp == 0 && !range.minInclusive())) {
                return "小于区间 " + range + " 的下界";
            }
        }
        if (!range.upperUnbounded()) {
            int cmp = Values.compareToBound(def.kind(), raw, value, range.max());
            if (cmp > 0 || (cmp == 0 && !range.maxInclusive())) {
                return "大于区间 " + range + " 的上界";
            }
        }
        return null;
    }

    /** 默认值本身也要满足格式；不满足说明格式描述写错了，是开发期问题。 */
    private Optional<Object> defaultValue(ValueFormat vf, String path, Run run) {
        TypeDef def = types.resolve(vf.typeName());
        if (def == null) {
            return Optional.of(vf.defaultValue());
        }
        String raw = vf.defaultValue();
        ValueFormat bare = new ValueFormat(vf.source(), vf.requirement(), vf.typeName(),
                vf.range(), vf.length(), null, vf.comment());
        String failure = evaluate(bare, def, raw, path, run);
        if (failure != null) {
            run.error(path, ErrorCode.FORMAT_DEFAULT_VALUE_INVALID,
                    "默认值 \"" + raw + "\" " + failure);
            return Optional.empty();
        }
        return Optional.of(coerceQuietly(def, raw));
    }

    private static Object coerceQuietly(TypeDef def, String raw) {
        try {
            return Values.coerce(def.kind(), raw);
        } catch (Values.ValueException e) {
            return raw;
        }
    }

    private static ErrorCode failureCode(String failure) {
        if (failure.startsWith("长度 ")) {
            return ErrorCode.DATA_OVER_LENGTH;
        }
        if (failure.startsWith("不在枚举")) {
            return ErrorCode.DATA_NOT_IN_SET_RANGE;
        }
        if (failure.startsWith("小于区间") || failure.startsWith("大于区间")) {
            return ErrorCode.DATA_NOT_IN_VALID_RANGE;
        }
        return ErrorCode.DATA_NOT_MATCHED;
    }

    // =================================================================
    // 杂项
    // =================================================================

    /**
     * 内置解析器只会因 JSON 语法失败，所以报语法错误码；换了解析器之后失败原因本库无从知道
     * （语法错？适配器自己的策略把它挡了？），所以报另一个码，真实原因放在 detail 里。
     */
    private ErrorCode readerErrorCode(ErrorCode builtinCode, ErrorCode pluggedCode) {
        return jsonReader == JsonReader.builtin() ? builtinCode : pluggedCode;
    }

    private static String child(String path, String name) {
        return path + "." + name;
    }

    private static String typeOf(Object data) {
        return switch (data) {
            case null -> "null";
            case Map<?, ?> ignored -> "对象";
            case List<?> ignored -> "数组";
            case String ignored -> "字符串";
            case Boolean ignored -> "布尔值";
            case Number ignored -> "数字";
            default -> data.getClass().getSimpleName();
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyMap(Map<?, ?> map) {
        return Collections.unmodifiableMap((Map<String, Object>) map);
    }

    /** 一次调用的问题收集器。放在实例外，保证 {@link Strapper} 本身无调用期状态。 */
    private static final class Run {
        final List<StrapIssue> errors = new ArrayList<>();
        final List<StrapIssue> warnings = new ArrayList<>();

        void error(String path, ErrorCode code, String detail) {
            errors.add(StrapIssue.of(path, code, detail));
        }

        void warn(String path, ErrorCode code, String detail) {
            warnings.add(StrapIssue.of(path, code, detail));
        }
    }
}
