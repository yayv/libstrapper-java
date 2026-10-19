package top.x0a.strapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import top.x0a.json.Json;

/**
 * 一次校验的结果。
 *
 * @param ok       是否没有 error 级问题（warning 不影响 ok）
 * @param value    按格式描述取出并归一化后的数据；顶层是 {@link Map} 或 {@link List}，失败时可能为 {@code null}
 * @param errors   导致校验失败的问题
 * @param warnings 不影响结果可用性、但值得知会的问题（用了默认值、丢弃了未定义的 key 等）
 */
public record StrapResult(boolean ok, Object value,
                          List<StrapIssue> errors, List<StrapIssue> warnings) {

    public StrapResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    static StrapResult failure(StrapIssue issue) {
        return new StrapResult(false, null, List.of(issue), List.of());
    }

    /** 顶层为对象时的取值；不是对象则抛 {@link IllegalStateException}。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> asMap() {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalStateException("顶层取值不是对象，实际为 " + describe(value));
    }

    /** 顶层为数组时的取值；不是数组则抛 {@link IllegalStateException}。 */
    @SuppressWarnings("unchecked")
    public List<Object> asList() {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalStateException("顶层取值不是数组，实际为 " + describe(value));
    }

    /**
     * 按路径取值，路径写法同错误信息里的 path，但不带开头的 {@code $.}：
     * {@code "data.list[0].modelId"}。取不到时返回空。
     */
    public Optional<Object> find(String path) {
        Object current = value;
        for (String token : tokenize(path)) {
            if (token.startsWith("[")) {
                int index = Integer.parseInt(token.substring(1, token.length() - 1));
                if (!(current instanceof List<?> list) || index < 0 || index >= list.size()) {
                    return Optional.empty();
                }
                current = list.get(index);
            } else {
                if (!(current instanceof Map<?, ?> map) || !map.containsKey(token)) {
                    return Optional.empty();
                }
                current = map.get(token);
            }
        }
        return Optional.ofNullable(current);
    }

    private static List<String> tokenize(String path) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String trimmed = path.startsWith("$.") ? path.substring(2) : path;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '.') {
                flush(tokens, current);
            } else if (c == '[') {
                flush(tokens, current);
                int close = trimmed.indexOf(']', i);
                if (close < 0) {
                    throw new IllegalArgumentException("路径里的 '[' 没有闭合: " + path);
                }
                tokens.add(trimmed.substring(i, close + 1));
                i = close;
            } else {
                current.append(c);
            }
        }
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    /** 取值的紧凑 JSON 形式。这里始终用内置的写出实现，与 {@link JsonReader} 换不换无关。 */
    public String toJson() {
        return Json.write(value);
    }

    /** 取值的缩进 JSON 形式。 */
    public String toPrettyJson() {
        return Json.writePretty(value);
    }

    /** 人读的问题清单，直接打到日志里就能定位。 */
    public String report() {
        if (errors.isEmpty() && warnings.isEmpty()) {
            return "校验通过，无告警";
        }
        StringBuilder sb = new StringBuilder();
        if (!errors.isEmpty()) {
            sb.append("错误 ").append(errors.size()).append(" 条:");
            for (StrapIssue issue : errors) {
                sb.append("\n  - ").append(issue);
            }
        }
        if (!warnings.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("告警 ").append(warnings.size()).append(" 条:");
            for (StrapIssue issue : warnings) {
                sb.append("\n  - ").append(issue);
            }
        }
        return sb.toString();
    }

    private static String describe(Object v) {
        return v == null ? "null" : v.getClass().getSimpleName();
    }
}
