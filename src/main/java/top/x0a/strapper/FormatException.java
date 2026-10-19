package top.x0a.strapper;

/** 格式描述本身写错时抛出。由 {@link Strapper} 捕获并转成 {@link StrapIssue}。 */
public final class FormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public FormatException(ErrorCode code, String detail) {
        super(code.message() + (detail == null || detail.isEmpty() ? "" : "：" + detail));
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    /** 去掉前缀后的补充说明。 */
    public String detail() {
        String message = getMessage();
        int sep = message.indexOf('：');
        return sep < 0 ? "" : message.substring(sep + 1);
    }
}
