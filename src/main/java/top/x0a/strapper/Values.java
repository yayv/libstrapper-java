package top.x0a.strapper;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import top.x0a.json.JsonWriter;
import top.x0a.strapper.Types.Kind;

/** 原始值 &rarr; 归一化取值 &rarr; 范围比较。{@link Strapper} 的取值细节都收在这里。 */
final class Values {

    private Values() {
    }

    /** 取值失败。仅在 {@link Values} 内部使用，由 {@link Strapper} 转成 {@link StrapIssue}。 */
    static final class ValueException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ValueException(String message) {
            super(message);
        }
    }

    // =================================================================
    // JSON 标量 -> 原始字符串
    // =================================================================

    /**
     * 把 JSON 里的标量转成待校验的原始字符串。
     *
     * <p>格式描述是按"字符串形态"来约定长度和正则的（spec：长度指原始值的字符串长度），
     * 所以无论 JSON 里写的是 {@code 123} 还是 {@code "123"}，都先归一成同一个原始串再校验。
     */
    static String raw(Object jsonValue) {
        return switch (jsonValue) {
            case null -> "";
            case String s -> s;
            case Boolean b -> b.toString();
            case Double d -> JsonWriter.number(d);
            case Float f -> JsonWriter.number(f.doubleValue());
            case Number n -> n.toString();
            default -> String.valueOf(jsonValue);
        };
    }

    // =================================================================
    // 原始字符串 -> 归一化取值
    // =================================================================

    /** 按根类型的语义把原始串转成最终取值。调用前应已通过正则校验。 */
    static Object coerce(Kind kind, String raw) {
        return switch (kind) {
            case INT -> parseLong(raw);
            case DECIMAL -> parseDouble(raw);
            case BOOL -> parseBool(raw);
            case TEXT -> raw;
            case DATE -> parseDate(raw).toString();
            case TIME -> formatTime(parseTime(raw));
            case DATETIME -> formatDateTime(raw);
        };
    }

    private static Long parseLong(String raw) {
        String s = raw.startsWith("+") ? raw.substring(1) : raw;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            throw new ValueException("超出 64 位整数范围或不是整数: " + raw);
        }
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new ValueException("不是合法的小数: " + raw);
        }
    }

    private static Boolean parseBool(String raw) {
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "true", "1", "yes" -> Boolean.TRUE;
            case "false", "0", "no" -> Boolean.FALSE;
            default -> throw new ValueException("不是合法的布尔值: " + raw);
        };
    }

    /** 接受 {@code 2020-01-02} / {@code 2020/1/2} / {@code 20200102}，统一产出 {@code LocalDate}。 */
    static LocalDate parseDate(String raw) {
        String s = raw.trim();
        String[] parts = s.split("[-/.]");
        try {
            if (parts.length == 3) {
                return LocalDate.of(Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
            if (parts.length == 1 && s.length() == 8) {
                return LocalDate.of(Integer.parseInt(s.substring(0, 4)),
                        Integer.parseInt(s.substring(4, 6)), Integer.parseInt(s.substring(6, 8)));
            }
        } catch (NumberFormatException | DateTimeException e) {
            throw new ValueException("不是真实存在的日期: " + raw);
        }
        throw new ValueException("无法识别的日期写法: " + raw);
    }

    /** 接受 {@code 12:34} / {@code 12:34:56}。 */
    static LocalTime parseTime(String raw) {
        String[] parts = raw.trim().split(":");
        try {
            if (parts.length == 2) {
                return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
            if (parts.length == 3) {
                return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]));
            }
        } catch (NumberFormatException | DateTimeException e) {
            throw new ValueException("不是真实存在的时间: " + raw);
        }
        throw new ValueException("无法识别的时间写法: " + raw);
    }

    /**
     * 归一化为 {@code yyyy-MM-dd HH:mm:ss}。
     *
     * <p>只写了日期时补 {@code 00:00:00}；带小数秒或时区后缀（{@code .000Z}、{@code +08:00}）时，
     * 取其墙上时钟部分，不做时区换算 —— 接口契约层面不该替调用方决定时区。
     */
    static String formatDateTime(String raw) {
        String s = raw.trim();
        int sep = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == 'T') {
                sep = i;
                break;
            }
        }
        if (sep < 0) {
            return parseDate(s) + " 00:00:00";
        }
        LocalDate date = parseDate(s.substring(0, sep));
        String timePart = s.substring(sep + 1);
        int cut = timePart.length();
        for (int i = 0; i < timePart.length(); i++) {
            char c = timePart.charAt(i);
            if (c == '.' || c == 'Z' || c == 'z' || c == '+') {
                cut = i;
                break;
            }
            // 时区的 '-' 一定出现在 "hh:mm" 之后，不会与时间自身的分隔符混淆
            if (c == '-' && i > 4) {
                cut = i;
                break;
            }
        }
        return date + " " + formatTime(parseTime(timePart.substring(0, cut)));
    }

    /** 统一补齐到 {@code HH:mm:ss}，{@code LocalTime.toString()} 在整分时会省掉秒。 */
    private static String formatTime(LocalTime t) {
        return pad(t.getHour()) + ":" + pad(t.getMinute()) + ":" + pad(t.getSecond());
    }

    private static String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    // =================================================================
    // 范围比较
    // =================================================================

    /** 枚举命中判断。数值类型额外按数值比较，这样 {@code int{1,2,3}} 能接受 {@code "01"}。 */
    static boolean inEnum(Kind kind, String raw, Object value, List<String> members) {
        String trimmed = raw.trim();
        for (String member : members) {
            if (member.equals(trimmed)) {
                return true;
            }
            if ((kind == Kind.INT || kind == Kind.DECIMAL) && numericEquals(member, trimmed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean numericEquals(String a, String b) {
        try {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 把取值与区间端点作比较。
     *
     * @return 负数/0/正数，语义同 {@link Comparable#compareTo}
     * @throws FormatException 端点本身写得不合法时
     */
    static int compareToBound(Kind kind, String raw, Object value, String bound) {
        switch (kind) {
            case INT, DECIMAL -> {
                BigDecimal left;
                BigDecimal right;
                try {
                    left = new BigDecimal(raw.startsWith("+") ? raw.substring(1) : raw);
                } catch (NumberFormatException e) {
                    throw new ValueException("不是合法的数值: " + raw);
                }
                try {
                    right = new BigDecimal(bound.startsWith("+") ? bound.substring(1) : bound);
                } catch (NumberFormatException e) {
                    throw new FormatException(ErrorCode.FORMAT_RANGE_SYNTAX_ERROR,
                            "区间端点不是数值: " + bound);
                }
                return left.compareTo(right);
            }
            case DATE -> {
                return String.valueOf(value).compareTo(normalizeBound(kind, bound));
            }
            case TIME, DATETIME -> {
                return String.valueOf(value).compareTo(normalizeBound(kind, bound));
            }
            default -> {
                return String.valueOf(value).compareTo(bound);
            }
        }
    }

    /** 端点按同一套规则归一化，这样 {@code date[2020/1/1,2020-12-31]} 两种写法都能比。 */
    private static String normalizeBound(Kind kind, String bound) {
        try {
            return String.valueOf(coerce(kind, bound));
        } catch (ValueException e) {
            throw new FormatException(ErrorCode.FORMAT_RANGE_SYNTAX_ERROR,
                    "区间端点不是合法的 " + kind + ": " + bound);
        }
    }
}
