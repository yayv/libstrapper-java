package top.x0a.strapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 类型表 —— 本库的扩展点。
 *
 * <p>类型分两层：
 * <ol>
 *   <li><b>基础类型</b>固定 9 个，决定一个值最终被解析成什么（{@link Kind}）：
 *       {@code int}、{@code float}、{@code number}、{@code bool}、
 *       {@code string}、{@code text}、{@code date}、{@code time}、{@code datetime}。</li>
 *   <li><b>扩展类型</b>就是一张「名 : 正则」的明文表，名字写成
 *       {@code <基础类型>.<扩展名>}，点号前面的部分说明这个值按什么语义取。
 *       一个值要先过基础类型的正则，再过扩展类型的正则。</li>
 * </ol>
 *
 * <p>spec 里列的 mobile、idcard、retCode…… 本身就是这么写出来的，内置表见
 * {@code top/x0a/strapper/types.txt}。业务领域的类型走完全相同的入口：
 *
 * <pre>{@code
 * // 共享电动车领域：车辆编号 9 位数字，前 4 位是生产批次
 * types.add("int.vehicleNo", "^20\\d{2}\\d{5}$");
 *
 * // 或者整张表从明文里读进来，跟接口文档放在一起维护
 * types.load(Path.of("conf/types.txt"));
 * }</pre>
 *
 * <p>格式描述里写全名 {@code "*int.vehicleNo"} 或短名 {@code "*vehicleNo"} 都可以；
 * 短名在全表内唯一时才能用，重名时必须写全名，避免歧义静默通过。
 * 同名再写一次即为覆盖，不满意内置规则就重写同名一行。
 *
 * <h2>string 与 text 的分工</h2>
 * <p>{@code string} 是纯字符串，不允许出现制表符、换行符这类转义字符；{@code text} 允许。
 * 内置表里凡是单行短串一律挂在 {@code string} 下，只有确实可能带换行的才挂到 {@code text} 下。
 * 这样需要收紧攻击面时，一句 {@link #disable(String) disable("text")} 就能把全部可带换行的
 * 类型一次性关掉：
 *
 * <pre>{@code
 * Types types = Types.standard().disable("text");   // 之后格式描述里写 text 或 text.* 一律报错
 * }</pre>
 */
public final class Types {

    /** 基础类型决定一个值最终被解析成什么。 */
    public enum Kind {
        /** 整数，结果为 {@link Long}。 */
        INT,
        /** 小数，结果为 {@link Double}。 */
        DECIMAL,
        /** 布尔，结果为 {@link Boolean}。 */
        BOOL,
        /** 文本，结果为 {@link String}。 */
        TEXT,
        /** 日期，结果为归一化的 {@code yyyy-MM-dd}。 */
        DATE,
        /** 时间，结果为归一化的 {@code HH:mm:ss}。 */
        TIME,
        /** 日期时间，结果为归一化的 {@code yyyy-MM-dd HH:mm:ss}。 */
        DATETIME
    }

    /** 一个可用的类型：基础类型本身，或一条扩展类型。 */
    public record TypeDef(String name, String base, Kind kind,
                          Pattern basePattern, Pattern pattern) {

        /** 是否为基础类型（没有额外的扩展正则）。 */
        public boolean isBase() {
            return pattern == null;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // =================================================================
    // 基础类型
    // =================================================================

    private static final Map<String, TypeDef> BASE = new LinkedHashMap<>();

    private static void base(String name, Kind kind, String regex) {
        BASE.put(name, new TypeDef(name, name, kind, Pattern.compile(regex), null));
    }

    static {
        base("int", Kind.INT, "^[+-]?\\d+$");
        base("float", Kind.DECIMAL, "^[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$");
        // number 是 float 的别名：日常说「基于 number 扩展」时不必纠结整数还是小数
        base("number", Kind.DECIMAL, "^[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$");
        base("bool", Kind.BOOL, "^(?i:true|false|1|0|yes|no)$");
        base("string", Kind.TEXT, "^[^\\x00-\\x1F\\x7F]*$");
        base("text", Kind.TEXT, "^[\\s\\S]*$");
        base("date", Kind.DATE, "^\\d{4}[-/.]?\\d{1,2}[-/.]?\\d{1,2}$");
        base("time", Kind.TIME, "^\\d{1,2}:\\d{2}(:\\d{2})?$");
        base("datetime", Kind.DATETIME,
                "^\\d{4}[-/.]?\\d{1,2}[-/.]?\\d{1,2}"
                        + "([ T]\\d{1,2}:\\d{2}(:\\d{2})?(\\.\\d{1,9})?(Z|[+-]\\d{2}:?\\d{2})?)?$");
    }

    /** 全部基础类型名。 */
    public static Set<String> baseNames() {
        return Collections.unmodifiableSet(BASE.keySet());
    }

    /** 内置扩展类型表所在的资源路径。 */
    public static final String BUILTIN_RESOURCE = "/top/x0a/strapper/types.txt";

    // =================================================================
    // 实例
    // =================================================================

    /** 扩展类型：全名 -> 正则原文，保持写入顺序。 */
    private final Map<String, String> extended = new LinkedHashMap<>();
    private final Map<String, Pattern> compiled = new LinkedHashMap<>();
    private final Map<String, Set<String>> shortIndex = new LinkedHashMap<>();
    /** 被禁用的类型：基础类型名或扩展类型全名。 */
    private final Set<String> disabled = new LinkedHashSet<>();

    private Types() {
    }

    /** 只有基础类型，扩展类型从零开始自己写。 */
    public static Types base() {
        return new Types();
    }

    /** 基础类型 + 内置扩展类型表（spec 中列出的全部扩展类型）。 */
    public static Types standard() {
        Types types = new Types();
        try (InputStream in = Types.class.getResourceAsStream(BUILTIN_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("找不到内置类型表资源: " + BUILTIN_RESOURCE);
            }
            types.load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读取内置类型表失败: " + BUILTIN_RESOURCE, e);
        }
        return types;
    }

    /** 复制一份，之后的改动互不影响。 */
    public Types copy() {
        Types copy = new Types();
        copy.extended.putAll(extended);
        copy.compiled.putAll(compiled);
        shortIndex.forEach((k, v) -> copy.shortIndex.put(k, new LinkedHashSet<>(v)));
        copy.disabled.addAll(disabled);
        return copy;
    }

    // =================================================================
    // 注册
    // =================================================================

    /**
     * 加一条扩展类型。
     *
     * @param name  点分全名，形如 {@code string.email}；点号前必须是基础类型名
     * @param regex 该类型的正则；值要同时满足基础类型的正则和它
     */
    public Types add(String name, String regex) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("类型名不能为空");
        }
        String trimmed = name.trim();
        int dot = trimmed.indexOf('.');
        if (dot <= 0 || dot == trimmed.length() - 1) {
            throw new IllegalArgumentException(
                    "扩展类型名必须写成 <基础类型>.<扩展名>，例如 string.email，实际为: " + trimmed);
        }
        String baseName = trimmed.substring(0, dot);
        if (!BASE.containsKey(baseName)) {
            throw new IllegalArgumentException("类型 " + trimmed + " 的基础类型不存在: " + baseName
                    + "，可用的基础类型: " + String.join(", ", BASE.keySet()));
        }
        if (regex == null || regex.isBlank()) {
            throw new IllegalArgumentException("类型 " + trimmed + " 没有写正则");
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex.trim());
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("类型 " + trimmed + " 的正则有语法错误: " + e.getMessage(), e);
        }
        extended.put(trimmed, regex.trim());
        compiled.put(trimmed, pattern);
        shortIndex.computeIfAbsent(trimmed.substring(dot + 1), k -> new LinkedHashSet<>()).add(trimmed);
        return this;
    }

    /** 批量加，键为全名，值为正则。 */
    public Types addAll(Map<String, String> entries) {
        entries.forEach(this::add);
        return this;
    }

    /**
     * 从明文读入一张类型表。每行一条 {@code 名 : 正则}，
     * {@code #} 开头的行是注释，空行忽略；名字与正则之间用第一个 {@code :} 或 {@code =} 分隔。
     */
    public Types load(String text) {
        int lineNo = 0;
        for (String line : text.split("\\R")) {
            lineNo++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            int equals = trimmed.indexOf('=');
            int sep = (colon < 0) ? equals : (equals < 0 ? colon : Math.min(colon, equals));
            if (sep <= 0) {
                throw new IllegalArgumentException(
                        "类型表第 " + lineNo + " 行缺少 ':' 或 '=' 分隔符: " + trimmed);
            }
            add(trimmed.substring(0, sep).trim(), trimmed.substring(sep + 1).trim());
        }
        return this;
    }

    /** 从文件读入一张类型表，UTF-8 编码。 */
    public Types load(Path file) throws IOException {
        return load(Files.readString(file, StandardCharsets.UTF_8));
    }

    // =================================================================
    // 禁用与启用
    // =================================================================

    /**
     * 禁用一个类型。之后格式描述里再写到它就会报
     * {@link ErrorCode#FORMAT_TYPE_DISABLED}。
     *
     * <p>禁用基础类型会连带禁用从它派生的全部扩展类型 —— {@code disable("text")}
     * 就是一次性关掉所有可能带换行、制表符的参数类型，需要收紧攻击面时很有用。
     *
     * <p>名字写错会直接抛异常而不是静默失败：安全相关的开关，悄悄没生效比报错危险得多。
     *
     * @param name 基础类型名、扩展类型全名或唯一短名
     */
    public Types disable(String name) {
        disabled.add(canonical(name));
        return this;
    }

    /** 解除禁用。 */
    public Types enable(String name) {
        disabled.remove(canonical(name));
        return this;
    }

    /** 该类型是不是被禁用了（基础类型被禁用时，从它派生的扩展类型也算被禁用）。 */
    public boolean isDisabled(String name) {
        if (disabled.isEmpty() || name == null || name.isEmpty()) {
            return false;
        }
        String full = fullName(name);
        if (full == null) {
            return false;
        }
        return disabled.contains(full) || disabled.contains(baseNameOf(full));
    }

    /** 当前被显式禁用的类型名。 */
    public Set<String> disabledTypes() {
        return Collections.unmodifiableSet(disabled);
    }

    /** 把短名归一成可存进 {@link #disabled} 的规范名；名字不存在时抛异常。 */
    private String canonical(String name) {
        String full = fullName(name);
        if (full == null) {
            throw new IllegalArgumentException("要禁用/启用的类型不存在: " + name
                    + "，可用的基础类型: " + String.join(", ", BASE.keySet()));
        }
        return full;
    }

    /** 名字 -> 规范全名；基础类型返回自身，扩展类型返回全名，解析不出来返回 {@code null}。 */
    private String fullName(String name) {
        if (BASE.containsKey(name)) {
            return name;
        }
        if (extended.containsKey(name)) {
            return name;
        }
        Set<String> candidates = shortIndex.get(name);
        return (candidates != null && candidates.size() == 1) ? candidates.iterator().next() : null;
    }

    private static String baseNameOf(String fullName) {
        int dot = fullName.indexOf('.');
        return dot < 0 ? fullName : fullName.substring(0, dot);
    }

    // =================================================================
    // 查询
    // =================================================================

    /**
     * 按基础类型名、扩展类型全名或唯一短名查找。
     * 找不到、短名有歧义、或该类型已被禁用时返回 {@code null}。
     */
    public TypeDef resolve(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String full = fullName(name);
        if (full == null) {
            return null;
        }
        String baseName = baseNameOf(full);
        if (disabled.contains(full) || disabled.contains(baseName)) {
            return null;
        }
        TypeDef base = BASE.get(baseName);
        if (full.equals(baseName)) {
            return base;
        }
        return new TypeDef(full, baseName, base.kind(), base.basePattern(), compiled.get(full));
    }

    /** {@link #resolve} 失败的原因，用于把「未定义」和「短名有歧义」区分开。 */
    public String resolveProblem(String name) {
        if (name == null || name.isEmpty()) {
            return "类型名为空";
        }
        Set<String> candidates = shortIndex.get(name);
        if (candidates != null && candidates.size() > 1) {
            return "短名 " + name + " 有歧义，请写全名，候选: " + String.join(", ", candidates);
        }
        String full = fullName(name);
        if (full != null) {
            String baseName = baseNameOf(full);
            if (disabled.contains(full)) {
                return "类型 " + full + " 已被禁用";
            }
            if (disabled.contains(baseName)) {
                return "类型 " + full + " 的基础类型 " + baseName + " 已被禁用";
            }
        }
        return "未定义的类型: " + name;
    }

    /** 是否可解析。 */
    public boolean has(String name) {
        return resolve(name) != null;
    }

    /** 全部扩展类型：全名 -> 正则原文。 */
    public Map<String, String> extendedTypes() {
        return Collections.unmodifiableMap(extended);
    }

    /** 把当前扩展类型表导出成明文，可直接落盘或打印。 */
    public String text() {
        StringBuilder sb = new StringBuilder();
        int width = extended.keySet().stream().mapToInt(String::length).max().orElse(0);
        extended.forEach((name, regex) ->
                sb.append(String.format("%-" + Math.max(width, 1) + "s : %s%n", name, regex)));
        return sb.toString();
    }

    // =================================================================
    // 校验
    // =================================================================

    /**
     * 先过基础类型的正则，再过扩展类型的正则。
     *
     * @return 通过返回 {@code null}，否则返回失败原因
     */
    public static String match(TypeDef def, String raw) {
        if (def.basePattern() != null && !def.basePattern().matcher(raw).matches()) {
            return "不符合基础类型 " + def.base() + " 的格式要求";
        }
        if (def.pattern() != null && !def.pattern().matcher(raw).matches()) {
            return "不符合 " + def.name() + " 的格式要求";
        }
        return null;
    }

    /** 只过基础类型的正则，用于在取值转换前先确认形态。 */
    static String matchBase(TypeDef def, String raw) {
        if (def.basePattern() != null && !def.basePattern().matcher(raw).matches()) {
            return "不符合基础类型 " + def.base() + " 的格式要求";
        }
        return null;
    }
}
