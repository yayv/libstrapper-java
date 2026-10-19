package top.x0a.strapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import top.x0a.strapper.JsonValue.*;

/**
 * libstrapper — Java 21 版
 *
 * valueFormat DSL（与 PHP 版同一语言）：
 *
 *   {"name":"*string//显示名","phone":"*string","note":"string//备注","...":"string"}
 *
 *   *        必填（仅 * 一个标记；PHP 文档中的 ! 属编写不一致，不实现）
 *   //注释   字段说明（保留在 FieldSpec 中，供文档/JSON Schema 导出）
 *   "..."    收尾键：未声明的键按该 spec 校验后收入结果
 *   嵌套     值为对象/数组即嵌套 dataStruct
 *
 * 类型三层降级（对话中确立的设计）：
 *   1. 标准类型（string/int/number/bool + 后续补充）—— 裸名永远归标准层
 *   2. 钩子（registerHook 注册；my.* 前缀约定归项目，但任何非标准名都会问询钩子）
 *   3. 字符串兜底 + warning（未知类型按 string 收，并记录警告——防笔误静默）
 *
 * ⚠ 净室实现声明：cls.webapi.php 内部实现未见过，本实现依据 example.md 的 DSL
 *   语义 + controller 调用面 + 本次会话确立的 spec。代码中 [DECISION] 标注的
 *   语义点需与 PHP 原版核对（即语义考古的 fixture 向量清单）。
 */
public final class Strapper {

    // =====================================================================
    // 结果与错误（records）
    // =====================================================================

    public record StrapError(String field, String code, String message) {
        public static final String BAD_FORMAT    = "BAD_FORMAT";
        public static final String BAD_JSON      = "BAD_JSON";
        public static final String MISSING       = "MISSING";
        public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    }

    public record StrapResult(
            boolean ok,
            Map<String, Object> values,
            List<StrapError> errors,
            List<String> warnings) {

        static StrapResult failure(StrapError e) {
            return new StrapResult(false, Map.of(), List.of(e), List.of());
        }
    }

    /** 字段规格：解析 format 后的产物；comment 保留供文档/Schema 导出。 */
    public record FieldSpec(
            String name,
            boolean required,
            String type,          // 叶子类型名；嵌套时为 "@object" / "@array"
            String comment,
            Map<String, FieldSpec> children,   // @object 时非空
            FieldSpec element) {               // @array 时非空
        public static final String T_OBJECT = "@object";
        public static final String T_ARRAY  = "@array";
    }

    // =====================================================================
    // 类型钩子（三层降级的第二层）
    // =====================================================================

    /**
     * 类型钩子：返回 null 表示"这个类型我不管"（继续降级）；
     * 返回 Handled 表示接管（值或类型错误二选一）。
     * 纯函数纪律：不得有副作用（对话中确立的钩子契约）。
     */
    @FunctionalInterface
    public interface TypeHook {
        Handled handle(String typeName, JsonValue value);

        record Handled(Object value, String error) {
            public static Handled ok(Object v)      { return new Handled(v, null); }
            public static Handled fail(String msg)  { return new Handled(null, msg); }
        }
    }

    private final List<TypeHook> hooks = new ArrayList<>();

    public Strapper registerHook(TypeHook hook) {
        hooks.add(hook);
        return this;
    }

    // =====================================================================
    // 主 API
    // =====================================================================

    /** 现代接口：format + JSON 文本 → StrapResult。 */
    public StrapResult strap(String format, String json) {
        // ---- 解析 format（format 本身是 JSON） ----
        Map<String, FieldSpec> spec;
        try {
            spec = parseFormat(format);
        } catch (Json.JsonException | IllegalArgumentException e) {
            return StrapResult.failure(
                new StrapError("", StrapError.BAD_FORMAT, "invalid format: " + e.getMessage()));
        }

        // ---- 解析输入 JSON ----
        JsonValue root;
        try {
            root = Json.parse(json);
        } catch (Json.JsonException e) {
            return StrapResult.failure(
                new StrapError("", StrapError.BAD_JSON, "invalid json: " + e.getMessage()));
        }
        if (!(root instanceof JObj obj)) {
            return StrapResult.failure(
                new StrapError("", StrapError.BAD_JSON, "top-level value must be an object"));
        }

        // ---- 按 spec 校验 ----
        List<StrapError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> out = validateObject(spec, obj, "", errors, warnings);
        return new StrapResult(errors.isEmpty(), out, List.copyOf(errors), List.copyOf(warnings));
    }

    // ---- PHP 兼容层：镜像 $t->getJSONParams($format,$string) / $t->isParseOk() ----

    private StrapResult lastResult;

    /** PHP 风格：成功返回值表，失败返回 null；细节经 isParseOk()/lastResult() 取。 */
    public Map<String, Object> getJSONParams(String format, String json) {
        lastResult = strap(format, json);
        return lastResult.ok() ? lastResult.values() : null;
    }

    public boolean isParseOk() {
        return lastResult != null && lastResult.ok();
    }

    public StrapResult lastResult() {
        return lastResult;
    }

    // =====================================================================
    // format 解析（DSL → FieldSpec 树）
    // =====================================================================

    /** 公开：供文档 / JSON Schema / MCP 工具定义导出使用。 */
    public static Map<String, FieldSpec> parseFormat(String format) {
        JsonValue v = Json.parse(format);
        if (!(v instanceof JObj obj))
            throw new IllegalArgumentException("format must be a JSON object");
        return parseFormatObject(obj);
    }

    private static Map<String, FieldSpec> parseFormatObject(JObj obj) {
        Map<String, FieldSpec> spec = new LinkedHashMap<>();
        for (var e : obj.fields().entrySet()) {
            String key = e.getKey();
            spec.put(key, parseFieldSpec(key, e.getValue()));
        }
        return spec;
    }

    private static FieldSpec parseFieldSpec(String name, JsonValue v) {
        return switch (v) {
            // 叶子："*string//注释"
            case JStr s -> parseLeafSpec(name, s.value());

            // 嵌套对象
            // [DECISION #5] 嵌套对象/数组暂无必填标记语法（* 在值串里，嵌套值不是串），
            //               v1 默认必填。可选嵌套的语法待与 PHP 原版核对后定。
            case JObj o -> new FieldSpec(name, true, FieldSpec.T_OBJECT, "",
                                         parseFormatObject(o), null);

            // 数组：取首元素作为元素 spec
            case JArr a -> {
                if (a.items().size() != 1)
                    throw new IllegalArgumentException(
                        "array spec for '" + name + "' must have exactly one element");
                yield new FieldSpec(name, true, FieldSpec.T_ARRAY, "",
                                    null, parseFieldSpec(name + "[]", a.items().get(0)));
            }

            default -> throw new IllegalArgumentException(
                "field '" + name + "': spec must be string, object, or array");
        };
    }

    /** 叶子 spec 语法：[*]typename[//comment] */
    private static FieldSpec parseLeafSpec(String name, String raw) {
        String s = raw;
        String comment = "";
        int slash = s.indexOf("//");
        if (slash >= 0) {
            comment = s.substring(slash + 2).trim();
            s = s.substring(0, slash);
        }
        s = s.trim();
        boolean required = false;
        if (s.startsWith("*")) {
            required = true;
            s = s.substring(1).trim();
        }
        if (s.isEmpty())
            throw new IllegalArgumentException("field '" + name + "': empty type name");
        return new FieldSpec(name, required, s, comment, null, null);
    }

    // =====================================================================
    // 校验（spec × JsonValue → Java 原生值）
    // =====================================================================

    private Map<String, Object> validateObject(
            Map<String, FieldSpec> spec, JObj obj, String path,
            List<StrapError> errors, List<String> warnings) {

        Map<String, Object> out = new LinkedHashMap<>();
        FieldSpec rest = spec.get("...");   // 收尾键

        // ---- 声明字段 ----
        for (var e : spec.entrySet()) {
            String key = e.getKey();
            if (key.equals("...")) continue;
            FieldSpec fs = e.getValue();
            String fpath = path.isEmpty() ? key : path + "." + key;

            JsonValue v = obj.fields().get(key);

            // [DECISION #4] JSON null 视同缺失（必填+null = MISSING）。
            boolean absent = (v == null) || (v instanceof JNull);

            if (absent) {
                if (fs.required()) {
                    errors.add(new StrapError(fpath, StrapError.MISSING,
                        "required field '" + fpath + "' is missing"));
                }
                // 可选缺失：结果中不出现该键
                //（PHP 实证：controller 用 isset($params->note) 判断，故可选缺失 = 键不存在）
                continue;
            }
            Object val = validateValue(fs, v, fpath, errors, warnings);
            if (val != SKIP) out.put(key, val);
        }

        // ---- 未声明字段 ----
        for (var e : obj.fields().entrySet()) {
            String key = e.getKey();
            if (spec.containsKey(key)) continue;
            if (rest != null) {
                String fpath = path.isEmpty() ? key : path + "." + key;
                // [DECISION #7] "..." 是收纳器不是校验器：声明 string 时对任意值
                //   宽松字符串化（"其余一律按字符串收"的语义，42 → "42"）；
                //   声明其他类型时仍正常校验。
                Object val = "string".equals(rest.type())
                    ? lenientString(e.getValue())
                    : validateValue(rest, e.getValue(), fpath, errors, warnings);
                if (val != SKIP) out.put(key, val);
            }
            // [DECISION #3] 无 "..." 时未声明键静默忽略（不入结果、不报错）。
        }
        return out;
    }

    /** 校验失败时的占位（错误已入 errors，值不入结果）。 */
    private static final Object SKIP = new Object();

    private Object validateValue(FieldSpec fs, JsonValue v, String path,
                                 List<StrapError> errors, List<String> warnings) {
        // ---- 嵌套 ----
        if (FieldSpec.T_OBJECT.equals(fs.type())) {
            if (v instanceof JObj o)
                return validateObject(fs.children(), o, path, errors, warnings);
            errors.add(typeErr(path, "object", v));
            return SKIP;
        }
        if (FieldSpec.T_ARRAY.equals(fs.type())) {
            if (v instanceof JArr a) {
                List<Object> list = new ArrayList<>(a.items().size());
                int idx = 0;
                for (JsonValue item : a.items()) {
                    Object val = validateValue(fs.element(), item,
                                               path + "[" + idx + "]", errors, warnings);
                    if (val != SKIP) list.add(val);
                    idx++;
                }
                return list;
            }
            errors.add(typeErr(path, "array", v));
            return SKIP;
        }

        // ---- 叶子：三层降级 ----

        // 第一层：标准类型（裸名永远归标准层，钩子不得遮蔽——对话确立）
        Standard std = standard(fs.type(), v);
        if (std != null) {
            if (std.error != null) {
                errors.add(new StrapError(path, StrapError.TYPE_MISMATCH,
                    "field '" + path + "': " + std.error));
                return SKIP;
            }
            return std.value;
        }

        // 第二层：钩子（my.* 及一切非标准名）
        for (TypeHook h : hooks) {
            TypeHook.Handled r = h.handle(fs.type(), v);
            if (r != null) {
                if (r.error() != null) {
                    errors.add(new StrapError(path, StrapError.TYPE_MISMATCH,
                        "field '" + path + "': " + r.error()));
                    return SKIP;
                }
                return r.value();
            }
        }

        // 第三层：字符串兜底 + warning（防 *phon 之类笔误被静默吞掉——对话确立）
        warnings.add("unknown type '" + fs.type() + "' at '" + path + "', treated as string");
        return lenientString(v);
    }

    // =====================================================================
    // 标准类型层
    // =====================================================================

    private record Standard(Object value, String error) {}

    /**
     * 标准类型校验。返回 null 表示"非标准类型"（进入降级）。
     * v1 集合刻意最小：string / int / number / bool。
     * color/length/point 等域类型按计划由 svgsketch PoC 喂出后再收编。
     */
    private static Standard standard(String type, JsonValue v) {
        return switch (type) {
            case "string" -> switch (v) {
                case JStr s -> new Standard(s.value(), null);
                // [DECISION #2] string 严格：数字/布尔不自动转字符串。
                //               PHP(json_decode 弱类型)可能更宽，需考古核对。
                default -> new Standard(null, "expected string, got " + kind(v));
            };
            case "int" -> switch (v) {
                case JNum n when n.integral() -> new Standard(n.asLong(), null);
                case JNum n -> new Standard(null, "expected int, got non-integral number");
                // [DECISION #1] int 接受纯数字字符串（"123" → 123）。
                //               贴近 PHP 弱类型血统的猜测实现，需考古核对。
                case JStr s when isIntegerString(s.value()) ->
                    new Standard(Long.parseLong(s.value().trim()), null);
                default -> new Standard(null, "expected int, got " + kind(v));
            };
            case "number" -> switch (v) {
                case JNum n -> new Standard(n.value(), null);
                case JStr s when isNumberString(s.value()) ->
                    new Standard(Double.parseDouble(s.value().trim()), null);
                default -> new Standard(null, "expected number, got " + kind(v));
            };
            case "bool" -> switch (v) {
                case JBool b -> new Standard(b.value(), null);
                // [DECISION #6] bool 严格：不接受 "true"/1。需考古核对。
                default -> new Standard(null, "expected bool, got " + kind(v));
            };
            default -> null;   // 非标准 → 降级
        };
    }

    private static boolean isIntegerString(String s) {
        s = s.trim();
        if (s.isEmpty()) return false;
        int i = s.charAt(0) == '-' ? 1 : 0;
        if (i == s.length()) return false;
        for (; i < s.length(); i++)
            if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    private static boolean isNumberString(String s) {
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 第三层兜底：任意值的宽松字符串化。 */
    private static String lenientString(JsonValue v) {
        return switch (v) {
            case JStr s  -> s.value();
            case JNum n  -> n.integral() ? String.valueOf(n.asLong()) : String.valueOf(n.value());
            case JBool b -> String.valueOf(b.value());
            case JNull ignored -> "";
            case JArr a  -> Json.encode(a);
            case JObj o  -> Json.encode(o);
        };
    }

    private static StrapError typeErr(String path, String expected, JsonValue got) {
        return new StrapError(path, StrapError.TYPE_MISMATCH,
            "field '" + path + "': expected " + expected + ", got " + kind(got));
    }

    private static String kind(JsonValue v) {
        return switch (v) {
            case JNull ignored -> "null";
            case JBool ignored -> "bool";
            case JNum ignored  -> "number";
            case JStr ignored  -> "string";
            case JArr ignored  -> "array";
            case JObj ignored  -> "object";
        };
    }
}
