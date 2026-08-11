package okhttp3.extension.logging;

/**
 * HTTP 调试日志的统一详细级别，语义与 OkHttp 官方日志拦截器保持一致。
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
public enum HttpLogLevel {

    /** 不记录 HTTP 调试日志。 */
    NONE,

    /** 仅记录请求方法、URL、响应状态、正文长度和耗时。 */
    BASIC,

    /** 在基础信息上记录经过脱敏的请求头和响应头。 */
    HEADERS,

    /** 在请求头信息上记录经过长度限制的文本请求体和响应体。 */
    BODY;

    /**
     * 判断当前级别是否包含指定级别的信息。
     *
     * @param required 待记录信息要求的最低级别
     * @return 当前级别不低于指定级别时返回 {@code true}
     */
    public boolean allows(HttpLogLevel required) {
        return required != null && ordinal() >= required.ordinal();
    }
}
