package top.x0a.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析与序列化，零第三方依赖。
 *
 * <p>这个类不依赖 {@code top.x0a.strapper} 里的任何东西，可以单独拿去用。
 * 它严格按 RFC 8259 解析，不接受单引号、尾逗号、注释，也不允许文档结束后还有多余内容 ——
 * 宽松解析对参数校验这类场景是安全弱化，所以刻意不做。
 *
 * <p>解析结果使用 JDK 原生类型表达，便于直接消费：
 * <ul>
 *   <li>object &rarr; {@link LinkedHashMap}（保持书写顺序，格式描述里 key 的先后有意义）</li>
 *   <li>array  &rarr; {@link ArrayList}</li>
 *   <li>string &rarr; {@link String}</li>
 *   <li>number &rarr; {@link Long}（整数）或 {@link Double}（含小数点/指数）</li>
 *   <li>true/false &rarr; {@link Boolean}，null &rarr; {@code null}</li>
 * </ul>
 */
public final class Json {

    private Json() {
    }

    /** JSON 语法错误。消息中带出错位置，便于定位格式描述里的笔误。 */
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final int position;

        JsonException(String message, int position) {
            super(message + " (位置 " + position + ")");
            this.position = position;
        }

        public int position() {
            return position;
        }
    }

    // =================================================================
    // 解析
    // =================================================================

    /**
     * 解析 JSON 文本。
     *
     * @param text 完整的 JSON 文档
     * @return Map / List / String / Long / Double / Boolean / {@code null} 构成的树
     * @throws JsonException 文本不是合法 JSON；消息里带出错位置
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("JSON 文本为 null", 0);
        }
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new JsonException("JSON 结束后存在多余内容", p.pos);
        }
        return value;
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
            this.pos = 0;
        }

        boolean atEnd() {
            return pos >= src.length();
        }

        void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private char peek() {
            if (atEnd()) {
                throw new JsonException("JSON 文本意外结束", pos);
            }
            return src.charAt(pos);
        }

        private void expect(char c) {
            if (atEnd() || src.charAt(pos) != c) {
                throw new JsonException("期望字符 '" + c + "'", pos);
            }
            pos++;
        }

        Object readValue() {
            char c = peek();
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    readLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    readLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    readLiteral("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        private void readLiteral(String literal) {
            if (!src.startsWith(literal, pos)) {
                throw new JsonException("期望字面量 " + literal, pos);
            }
            pos += literal.length();
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (!atEnd() && peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return map;
                } else {
                    throw new JsonException("对象中期望 ',' 或 '}'", pos);
                }
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (!atEnd() && peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return list;
                } else {
                    throw new JsonException("数组中期望 ',' 或 ']'", pos);
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("字符串未闭合", pos);
                }
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw new JsonException("转义符后意外结束", pos);
                }
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/'  -> sb.append('/');
                    case 'b'  -> sb.append('\b');
                    case 'f'  -> sb.append('\f');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'u'  -> {
                        if (pos + 4 > src.length()) {
                            throw new JsonException("\\u 转义不完整", pos);
                        }
                        String hex = src.substring(pos, pos + 4);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw new JsonException("\\u 转义不是合法的十六进制: " + hex, pos);
                        }
                        pos += 4;
                    }
                    default -> throw new JsonException("无法识别的转义字符 \\" + esc, pos - 1);
                }
            }
        }

        private Object readNumber() {
            int start = pos;
            if (!atEnd() && (peek() == '-' || peek() == '+')) {
                pos++;
            }
            boolean floating = false;
            while (!atEnd()) {
                char c = src.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    floating = floating || c == '.' || c == 'e' || c == 'E';
                    pos++;
                } else {
                    break;
                }
            }
            String token = src.substring(start, pos);
            if (token.isEmpty()) {
                throw new JsonException("无法识别的值", start);
            }
            try {
                if (floating) {
                    return Double.valueOf(token);
                }
                return Long.valueOf(token);
            } catch (NumberFormatException e) {
                throw new JsonException("数字格式错误: " + token, start);
            }
        }
    }

    // =================================================================
    // 序列化 —— 实现都在 JsonWriter 里，这里只是两个常用入口的快捷方式
    // =================================================================

    /** 紧凑输出。等价于 {@code JsonWriter.compact().write(value)}。 */
    public static String write(Object value) {
        return JsonWriter.compact().write(value);
    }

    /** 缩进输出，便于人读。等价于 {@code JsonWriter.pretty().write(value)}。 */
    public static String writePretty(Object value) {
        return JsonWriter.pretty().write(value);
    }
}
