package top.x0a.strapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import top.x0a.strapper.JsonValue.*;

/**
 * 最小 JSON 解析器 + 序列化器，零依赖。
 *
 * parse   : String → JsonValue          （对应 PHP json_decode）
 * encode  : Object → String             （对应 PHP json_encode —— 补齐 Java 缺的那一句）
 *
 * 支持完整 JSON 语法（字符串转义含 \\uXXXX），不支持注释/尾逗号等扩展。
 */
public final class Json {

    private Json() {}

    // =====================================================================
    // 解析
    // =====================================================================

    public static class JsonException extends RuntimeException {
        public final int pos;
        public JsonException(String msg, int pos) {
            super(msg + " (at offset " + pos + ")");
            this.pos = pos;
        }
    }

    public static JsonValue parse(String text) {
        Parser p = new Parser(text);
        JsonValue v = p.parseValue();
        p.skipWs();
        if (!p.eof()) throw new JsonException("trailing characters after JSON value", p.i);
        return v;
    }

    private static final class Parser {
        final String s;
        int i = 0;

        Parser(String s) { this.s = s; }

        boolean eof() { return i >= s.length(); }

        char peek() {
            if (eof()) throw new JsonException("unexpected end of input", i);
            return s.charAt(i);
        }

        char next() {
            char c = peek();
            i++;
            return c;
        }

        void skipWs() {
            while (!eof()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        void expect(char c) {
            if (next() != c) throw new JsonException("expected '" + c + "'", i - 1);
        }

        JsonValue parseValue() {
            skipWs();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> new JStr(parseString());
                case 't' -> { expectWord("true");  yield new JBool(true); }
                case 'f' -> { expectWord("false"); yield new JBool(false); }
                case 'n' -> { expectWord("null");  yield new JNull(); }
                default  -> parseNumber();
            };
        }

        void expectWord(String w) {
            if (!s.startsWith(w, i)) throw new JsonException("invalid literal", i);
            i += w.length();
        }

        JsonValue parseObject() {
            expect('{');
            Map<String, JsonValue> fields = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') { i++; return new JObj(fields); }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                JsonValue val = parseValue();
                fields.put(key, val);
                skipWs();
                char c = next();
                if (c == '}') return new JObj(fields);
                if (c != ',') throw new JsonException("expected ',' or '}'", i - 1);
            }
        }

        JsonValue parseArray() {
            expect('[');
            List<JsonValue> items = new ArrayList<>();
            skipWs();
            if (peek() == ']') { i++; return new JArr(items); }
            while (true) {
                items.add(parseValue());
                skipWs();
                char c = next();
                if (c == ']') return new JArr(items);
                if (c != ',') throw new JsonException("expected ',' or ']'", i - 1);
            }
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"'  -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/'  -> sb.append('/');
                        case 'b'  -> sb.append('\b');
                        case 'f'  -> sb.append('\f');
                        case 'n'  -> sb.append('\n');
                        case 'r'  -> sb.append('\r');
                        case 't'  -> sb.append('\t');
                        case 'u'  -> {
                            if (i + 4 > s.length()) throw new JsonException("bad \\u escape", i);
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new JsonException("bad escape '\\" + e + "'", i - 1);
                    }
                } else if (c < 0x20) {
                    throw new JsonException("raw control char in string", i - 1);
                } else {
                    sb.append(c);
                }
            }
        }

        JsonValue parseNumber() {
            int start = i;
            if (!eof() && peek() == '-') i++;
            while (!eof() && Character.isDigit(s.charAt(i))) i++;
            boolean integral = true;
            if (!eof() && s.charAt(i) == '.') {
                integral = false;
                i++;
                while (!eof() && Character.isDigit(s.charAt(i))) i++;
            }
            if (!eof() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                integral = false;
                i++;
                if (!eof() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                while (!eof() && Character.isDigit(s.charAt(i))) i++;
            }
            String lex = s.substring(start, i);
            if (lex.isEmpty() || lex.equals("-"))
                throw new JsonException("invalid number", start);
            return new JNum(Double.parseDouble(lex), integral);
        }
    }

    // =====================================================================
    // 序列化（java 版的 json_encode）
    // =====================================================================

    /**
     * 接受 Java 原生对象图：null / String / Number / Boolean / Map / List / JsonValue。
     * Map 的 key 统一 toString；保序（LinkedHashMap 传入即保序输出）。
     */
    public static String encode(Object v) {
        StringBuilder sb = new StringBuilder();
        encodeInto(v, sb);
        return sb.toString();
    }

    private static void encodeInto(Object v, StringBuilder sb) {
        switch (v) {
            case null            -> sb.append("null");
            case String s        -> encodeString(s, sb);
            case Boolean b       -> sb.append(b);
            case Double d        -> encodeDouble(d, sb);
            case Float f         -> encodeDouble(f.doubleValue(), sb);
            case Number n        -> sb.append(n);          // Integer/Long/BigInteger…
            case Map<?, ?> m     -> {
                sb.append('{');
                boolean first = true;
                for (var e : m.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    encodeString(String.valueOf(e.getKey()), sb);
                    sb.append(':');
                    encodeInto(e.getValue(), sb);
                }
                sb.append('}');
            }
            case List<?> l       -> {
                sb.append('[');
                boolean first = true;
                for (Object item : l) {
                    if (!first) sb.append(',');
                    first = false;
                    encodeInto(item, sb);
                }
                sb.append(']');
            }
            case JsonValue jv    -> encodeJsonValue(jv, sb);
            default -> throw new IllegalArgumentException(
                "Json.encode: unsupported type " + v.getClass().getName());
        }
    }

    private static void encodeJsonValue(JsonValue v, StringBuilder sb) {
        switch (v) {
            case JNull ignored   -> sb.append("null");
            case JBool b         -> sb.append(b.value());
            case JNum n          -> { if (n.integral()) sb.append(n.asLong()); else encodeDouble(n.value(), sb); }
            case JStr s          -> encodeString(s.value(), sb);
            case JArr a          -> encodeInto(a.items(), sb);
            case JObj o          -> encodeInto(o.fields(), sb);
        }
    }

    private static void encodeDouble(double d, StringBuilder sb) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            sb.append((long) d);   // 1.0 → 1，贴近 json_encode 行为
        } else {
            sb.append(d);
        }
    }

    private static void encodeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
