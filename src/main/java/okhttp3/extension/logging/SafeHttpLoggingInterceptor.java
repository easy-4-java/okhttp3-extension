package okhttp3.extension.logging;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 提供有界正文和敏感请求头脱敏能力的 OkHttp 应用拦截器。
 *
 * <p>该拦截器沿用 {@link HttpLogLevel} 的 NONE/BASIC/HEADERS/BODY 语义，但不会像原生
 * BODY 日志一样无界输出正文。响应体通过 {@link Response#peekBody(long)} 读取副本，不会消费业务响应流。</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
public final class SafeHttpLoggingInterceptor implements Interceptor {

    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final String REDACTED = "<redacted>";
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();
    private static final Set<String> DEFAULT_REDACTED_HEADERS = new HashSet<>(Arrays.asList(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key",
            "x-auth-token", "x-openclaw-token"));

    private final Logger logger;
    private final HttpLogLevel level;
    private final int maxContentLength;
    private final Set<String> redactedHeaders = new HashSet<>(DEFAULT_REDACTED_HEADERS);

    /**
     * 创建安全 HTTP 日志拦截器。
     *
     * @param loggerName SLF4J 日志器名称，通常传入所属 SDK 客户端类名
     * @param level HTTP 日志详细级别
     * @param maxContentLength BODY 级别单项正文最大字符数
     */
    public SafeHttpLoggingInterceptor(String loggerName, HttpLogLevel level, int maxContentLength) {
        this(LoggerFactory.getLogger(loggerName), level, maxContentLength);
    }

    SafeHttpLoggingInterceptor(Logger logger, HttpLogLevel level, int maxContentLength) {
        this.logger = logger;
        this.level = level == null ? HttpLogLevel.NONE : level;
        this.maxContentLength = Math.max(1, maxContentLength);
    }

    /**
     * 增加需要脱敏的请求头名称，名称匹配不区分大小写。
     *
     * @param headerName 请求头名称
     * @return 当前拦截器
     */
    public SafeHttpLoggingInterceptor redactHeader(String headerName) {
        if (headerName != null && !headerName.trim().isEmpty()) {
            redactedHeaders.add(headerName.trim().toLowerCase(Locale.ROOT));
        }
        return this;
    }

    /**
     * 记录一次完整的 OkHttp 调用，同时保持响应体可供业务代码继续读取。
     *
     * @param chain OkHttp 拦截器链
     * @return 下游返回的原始响应
     * @throws IOException 网络调用失败时原样抛出
     */
    @Override
    public Response intercept(Chain chain) throws IOException {
        if (level == HttpLogLevel.NONE || !logger.isDebugEnabled()) {
            return chain.proceed(chain.request());
        }

        long requestId = REQUEST_SEQUENCE.incrementAndGet();
        long startedAt = System.nanoTime();
        Request request = chain.request();
        logger.debug("HTTP request started: requestId={}, method={}, url={}",
                requestId, request.method(), request.url());
        if (level.allows(HttpLogLevel.HEADERS)) {
            logger.debug("HTTP request headers: requestId={}, headers={}", requestId, formatHeaders(request.headers()));
        }
        if (level.allows(HttpLogLevel.BODY)) {
            logger.debug("HTTP request body: requestId={}, body={}", requestId, readRequestBody(request));
        }

        try {
            Response response = chain.proceed(request);
            ResponseBody responseBody = response.body();
            long bodyLength = responseBody == null ? 0L : responseBody.contentLength();
            logger.debug("HTTP request completed: requestId={}, method={}, url={}, status={}, bodyLength={}, elapsedMs={}",
                    requestId, request.method(), request.url(), response.code(), bodyLength, elapsedMillis(startedAt));
            if (level.allows(HttpLogLevel.HEADERS)) {
                logger.debug("HTTP response headers: requestId={}, headers={}",
                        requestId, formatHeaders(response.headers()));
            }
            if (level.allows(HttpLogLevel.BODY)) {
                logger.debug("HTTP response body: requestId={}, body={}", requestId, readResponseBody(response));
            }
            return response;
        } catch (IOException error) {
            logger.debug("HTTP request failed: requestId={}, method={}, url={}, elapsedMs={}, error={}",
                    requestId, request.method(), request.url(), elapsedMillis(startedAt), error.getMessage());
            throw error;
        }
    }

    private String formatHeaders(Headers headers) {
        StringBuilder result = new StringBuilder("{");
        for (int index = 0; index < headers.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            String name = headers.name(index);
            result.append(name).append('=').append(isRedacted(name) ? REDACTED : headers.value(index));
        }
        return result.append('}').toString();
    }

    private String readRequestBody(Request request) {
        RequestBody body = request.body();
        if (body == null) {
            return "<empty>";
        }
        if (body.isDuplex() || body.isOneShot()) {
            return "<one-shot or duplex body omitted>";
        }
        if (!isText(body.contentType())) {
            return "<binary body omitted>";
        }
        try {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return truncate(buffer.readString(resolveCharset(body.contentType())));
        } catch (IOException error) {
            return "<body unavailable: " + error.getMessage() + ">";
        }
    }

    private String readResponseBody(Response response) {
        ResponseBody body = response.body();
        if (body == null) {
            return "<empty>";
        }
        if (!isText(body.contentType())) {
            return "<binary body omitted>";
        }
        try (ResponseBody preview = response.peekBody(Math.max(1L, (long) maxContentLength * 4L))) {
            return truncate(preview.string());
        } catch (IOException error) {
            return "<body unavailable: " + error.getMessage() + ">";
        }
    }

    private boolean isRedacted(String name) {
        return redactedHeaders.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean isText(MediaType mediaType) {
        if (mediaType == null) {
            return true;
        }
        String type = mediaType.type().toLowerCase(Locale.ROOT);
        String subtype = mediaType.subtype().toLowerCase(Locale.ROOT);
        return "text".equals(type) || subtype.contains("json") || subtype.contains("xml")
                || subtype.contains("form") || subtype.contains("graphql") || subtype.contains("javascript");
    }

    private Charset resolveCharset(MediaType mediaType) {
        return mediaType == null ? UTF_8 : mediaType.charset(UTF_8);
    }

    private String truncate(String content) {
        if (content == null || content.length() <= maxContentLength) {
            return content;
        }
        return content.substring(0, maxContentLength) + "...<truncated>";
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
