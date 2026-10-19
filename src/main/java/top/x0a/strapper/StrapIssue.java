package top.x0a.strapper;

/**
 * 一条校验问题。
 *
 * @param path   出问题的位置，形如 {@code $.data.list[0].modelId}；根为 {@code $}
 * @param code   问题码
 * @param detail 针对本次问题的补充说明（可能为空串）
 */
public record StrapIssue(String path, ErrorCode code, String detail) {

    public StrapIssue {
        path = path == null ? "$" : path;
        detail = detail == null ? "" : detail;
    }

    static StrapIssue of(String path, ErrorCode code) {
        return new StrapIssue(path, code, "");
    }

    static StrapIssue of(String path, ErrorCode code, String detail) {
        return new StrapIssue(path, code, detail);
    }

    @Override
    public String toString() {
        String head = path + ": [" + code.name() + "] " + code.message();
        return detail.isEmpty() ? head : head + " —— " + detail;
    }
}
