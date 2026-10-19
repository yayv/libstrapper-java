package top.x0a.strapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一个 key 的格式描述，对应 valueFormat.md / dataStruct.md 里 key 上可以带的标记：
 *
 * <pre>
 *   "[*|!][[n]|[n,m]]&lt;key名&gt;"
 * </pre>
 *
 * <ul>
 *   <li>{@code "*carData"} —— 值为对象或数组时，必填性标在 key 上（dataStruct 特殊情况二）</li>
 *   <li>{@code "*[4,9]fruits"} —— 必填，且数组长度在 4~9 之间；{@code "[n]"} 表示长度恰为 n</li>
 *   <li>{@code "..."} —— 本级接受未在格式中列出的 key（{@link Kind#MORE_KEYS}）</li>
 *   <li>{@code "!string"} —— key 名本身不确定，只约定 key 要符合某个类型（{@link Kind#WILDCARD}）</li>
 * </ul>
 *
 * @param source      原始 key 串
 * @param kind        这个 key 的种类
 * @param requirement 必填性
 * @param name        普通 key 的名字；{@code MORE_KEYS}/{@code WILDCARD} 时无意义
 * @param minItems    值为数组时的最小长度，未约定为 {@code null}
 * @param maxItems    值为数组时的最大长度，未约定为 {@code null}
 * @param keyType     {@code WILDCARD} 时，key 名自身要满足的类型名
 */
public record KeyFormat(String source, Kind kind, Requirement requirement, String name,
                        Integer minItems, Integer maxItems, String keyType) {

    /** key 的种类。 */
    public enum Kind {
        /** 普通 key。 */
        NORMAL,
        /** {@code "..."}：允许出现未定义的 key。 */
        MORE_KEYS,
        /** {@code "!<类型>"}：key 名不确定，只约束 key 的类型。 */
        WILDCARD
    }

    /** 允许出现未定义 key 的特殊 key。 */
    public static final String MORE_KEYS_TOKEN = "...";

    private static final Pattern PATTERN = Pattern.compile(
            "^\\s*([*!])?"                                       // 1 必填标记
                    + "\\s*(?:\\[\\s*(\\d*)\\s*(?:,\\s*(\\d*)\\s*)?\\])?"  // 2/3 数组长度
                    + "\\s*(.*?)\\s*$",                          // 4 key 名
            Pattern.DOTALL);

    /**
     * 解析一个 key。
     *
     * <p>{@code !} 在 key 上有两种读法：{@code !string} 是"key 名不确定"（dataStruct），
     * 而 {@code !mobileVerified} 是"条件必填"（valueFormat）。消歧规则：
     * <b>标记为 {@code !}、没有数组长度约束、且剩余部分恰好是一个已注册的类型名</b> 时按通配 key 处理，
     * 否则按条件必填的普通 key 处理。
     *
     * @param types 用于判断 {@code !xxx} 里的 xxx 是不是类型名；传 {@code null} 则一律按普通 key 处理
     */
    public static KeyFormat parse(String source, Types types) {
        if (source == null) {
            throw new FormatException(ErrorCode.FORMAT_KEY_SYNTAX_ERROR, "key 为 null");
        }
        if (MORE_KEYS_TOKEN.equals(source.trim())) {
            return new KeyFormat(source, Kind.MORE_KEYS, Requirement.OPTIONAL,
                    MORE_KEYS_TOKEN, null, null, null);
        }

        Matcher m = PATTERN.matcher(source);
        if (!m.matches()) {
            throw new FormatException(ErrorCode.FORMAT_KEY_SYNTAX_ERROR, "无法解析: " + source);
        }

        Requirement requirement = Requirement.of(m.group(1));
        String name = m.group(4);
        if (name == null || name.isEmpty()) {
            throw new FormatException(ErrorCode.FORMAT_KEY_SYNTAX_ERROR, "key 名为空: " + source);
        }

        boolean hasBrackets = m.group(2) != null || m.group(3) != null;
        Integer minItems = null;
        Integer maxItems = null;
        if (hasBrackets) {
            String lo = m.group(2) == null ? "" : m.group(2);
            String hi = m.group(3);
            if (hi == null) {
                // "[n]"：长度恰为 n
                if (!lo.isEmpty()) {
                    minItems = maxItems = Integer.valueOf(lo);
                }
            } else {
                // "[n,m]"：任一端写空表示该侧不限
                if (!lo.isEmpty()) {
                    minItems = Integer.valueOf(lo);
                }
                if (!hi.isEmpty()) {
                    maxItems = Integer.valueOf(hi);
                }
            }
            if (minItems != null && maxItems != null && minItems > maxItems) {
                throw new FormatException(ErrorCode.FORMAT_KEY_SYNTAX_ERROR,
                        "数组长度下界大于上界: " + source);
            }
        }

        if (requirement == Requirement.CONDITIONAL && !hasBrackets
                && types != null && types.has(name)) {
            return new KeyFormat(source, Kind.WILDCARD, Requirement.OPTIONAL, name, null, null, name);
        }

        return new KeyFormat(source, Kind.NORMAL, requirement, name, minItems, maxItems, null);
    }

    /** 是否对数组长度做了约定。 */
    public boolean hasItemsRange() {
        return minItems != null || maxItems != null;
    }

    /** 数组长度是否落在约定内。 */
    public boolean acceptsSize(int size) {
        if (minItems != null && size < minItems) {
            return false;
        }
        return maxItems == null || size <= maxItems;
    }

    /** 人读的长度约定描述，用于错误信息。 */
    public String itemsRangeText() {
        if (minItems != null && minItems.equals(maxItems)) {
            return "恰好 " + minItems + " 个";
        }
        if (minItems != null && maxItems != null) {
            return minItems + "~" + maxItems + " 个";
        }
        if (minItems != null) {
            return "至少 " + minItems + " 个";
        }
        if (maxItems != null) {
            return "至多 " + maxItems + " 个";
        }
        return "不限";
    }
}
