package top.x0a.strapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一条值的格式描述，对应 valueFormat.md 的语法：
 *
 * <pre>
 *   "[*|!]&lt;格式名&gt;[数值范围][:长度][#默认值]//说明"
 * </pre>
 *
 * 例如 {@code "*string{男,女}:4#男//性别"}、{@code "*int[1990,2030]:4//年份"}、
 * {@code "mobile//手机号"}。
 *
 * @param source       原始格式串
 * @param requirement  必填性
 * @param typeName     格式名，可以是短名（{@code mobile}）或全名（{@code string.mobile}）
 * @param range        取值范围，没写时为 {@code null}
 * @param length       原始值允许的最大字符长度，没写时为 {@code null}
 * @param defaultValue 取值失败时兜底用的默认值，没写时为 {@code null}
 * @param comment      给产品、业务同学看的说明
 */
public record ValueFormat(String source, Requirement requirement, String typeName,
                          Range range, Integer length, String defaultValue, String comment) {

    /**
     * 取值范围。两种形态：
     * <ul>
     *   <li>枚举 {@code {男,女}} —— {@link #enumeration()} 为 true，成员在 {@link #members()}</li>
     *   <li>区间 {@code (1,100]} —— 端点在 {@link #min()}/{@link #max()}，
     *       开闭由 {@link #minInclusive()}/{@link #maxInclusive()} 决定；端点写空表示该侧无界</li>
     * </ul>
     */
    public record Range(boolean enumeration, List<String> members,
                        String min, String max, boolean minInclusive, boolean maxInclusive,
                        String source) {

        public Range {
            members = members == null ? List.of() : List.copyOf(members);
        }

        /** 该侧是否无界。 */
        public boolean lowerUnbounded() {
            return min == null || min.isEmpty();
        }

        public boolean upperUnbounded() {
            return max == null || max.isEmpty();
        }

        @Override
        public String toString() {
            return source;
        }
    }

    // 各段都是可选的，因此整体靠 $ 锚定：写错了会整体匹配失败，而不是静默截取一半。
    private static final Pattern PATTERN = Pattern.compile(
            "^\\s*([*!])?"                          // 1 必填标记
                    + "\\s*([^\\s{\\[():#/]*)"      // 2 格式名（可含 '.'，如 int.vehicleNo）
                    + "\\s*(?:([{\\[(])([^}\\])]*)([}\\])]))?"  // 3/4/5 范围：左括号、内容、右括号
                    + "\\s*(?::\\s*(\\d+))?"        // 6 长度
                    + "\\s*(?:#([^/]*))?"           // 7 默认值（不含 '/'，以免与 // 说明冲突）
                    + "\\s*(?://(.*))?$",           // 8 说明
            Pattern.DOTALL);

    /** 解析一条值格式描述；语法错误时抛 {@link FormatException}。 */
    public static ValueFormat parse(String source) {
        if (source == null) {
            throw new FormatException(ErrorCode.FORMAT_VALUEFORMAT_SYNTAX_ERROR, "格式串为 null");
        }
        Matcher m = PATTERN.matcher(source);
        if (!m.matches()) {
            throw new FormatException(ErrorCode.FORMAT_VALUEFORMAT_SYNTAX_ERROR, "无法解析: " + source);
        }

        Requirement requirement = Requirement.of(m.group(1));
        String typeName = m.group(2) == null ? "" : m.group(2).trim();
        if (typeName.isEmpty()) {
            throw new FormatException(ErrorCode.FORMAT_VALUEFORMAT_SYNTAX_ERROR,
                    "没有写格式名: " + source);
        }

        Range range = m.group(3) == null
                ? null
                : parseRange(m.group(3), m.group(4), m.group(5), source);

        Integer length = null;
        if (m.group(6) != null) {
            try {
                length = Integer.valueOf(m.group(6));
            } catch (NumberFormatException e) {
                throw new FormatException(ErrorCode.FORMAT_VALUEFORMAT_SYNTAX_ERROR,
                        "长度不是整数: " + m.group(6));
            }
        }

        // "#" 后面写空串表示"有这一段但默认值为空"，与完全不写 "#" 区分开
        String defaultValue = m.group(7);
        String comment = m.group(8) == null ? "" : m.group(8).trim();

        return new ValueFormat(source, requirement, typeName, range, length, defaultValue, comment);
    }

    private static Range parseRange(String left, String body, String right, String source) {
        String content = body == null ? "" : body;
        if ("{".equals(left)) {
            if (!"}".equals(right)) {
                throw new FormatException(ErrorCode.FORMAT_RANGE_SYNTAX_ERROR,
                        "枚举必须以 '}' 结束: " + source);
            }
            List<String> members = new ArrayList<>();
            for (String item : content.split(",", -1)) {
                members.add(item.trim());
            }
            if (members.size() == 1 && members.get(0).isEmpty()) {
                throw new FormatException(ErrorCode.FORMAT_RANGE_SYNTAX_ERROR,
                        "枚举没有列出任何取值: " + source);
            }
            return new Range(true, members, null, null, false, false, left + content + right);
        }

        if ("}".equals(right)) {
            throw new FormatException(ErrorCode.FORMAT_RANGE_SYNTAX_ERROR,
                    "区间不能以 '}' 结束: " + source);
        }
        int comma = content.indexOf(',');
        if (comma < 0) {
            throw new FormatException(ErrorCode.FORMAT_RANGE_SYNTAX_ERROR,
                    "区间必须写成 <下界>,<上界> 的形式: " + source);
        }
        String min = content.substring(0, comma).trim();
        String max = content.substring(comma + 1).trim();
        if (max.indexOf(',') >= 0) {
            throw new FormatException(ErrorCode.FORMAT_RANGE_SYNTAX_ERROR,
                    "区间只能有一个逗号: " + source);
        }
        return new Range(false, Collections.emptyList(), min, max,
                "[".equals(left), "]".equals(right), left + content + right);
    }

    /** 是否写了默认值（{@code #} 后面即使是空串也算写了）。 */
    public boolean hasDefault() {
        return defaultValue != null;
    }
}
