package okhttp3.extension.interceptor;

import okhttp3.*;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OkHttp interceptor that compresses request body with gzip.
 */
public class GzipRequestInterceptor implements RequestInterceptor {

    private static final long DEFAULT_MINIMUM_BODY_BYTES = 1_024L;

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final long minimumBodyBytes;

    public GzipRequestInterceptor(boolean enabled) {
        this(enabled, DEFAULT_MINIMUM_BODY_BYTES);
    }

    public GzipRequestInterceptor(boolean enabled, long minimumBodyBytes) {
        this.enabled.set(enabled);
        this.minimumBodyBytes = Math.max(0L, minimumBodyBytes);
    }

    public void enable() {
        enabled.set(true);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void disable() {
        enabled.set(false);
    }

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        if (!enabled.get()) {
            return chain.proceed(chain.request());
        }
        Request originalRequest = chain.request();
        RequestBody body = originalRequest.body();
        if (body == null || originalRequest.header("Content-Encoding") != null
                || body.isOneShot() || body.isDuplex()) {
            return chain.proceed(originalRequest);
        }
        long contentLength = body.contentLength();
        if (contentLength >= 0L && contentLength < minimumBodyBytes) {
            return chain.proceed(originalRequest);
        }
        Request compressedRequest = originalRequest.newBuilder()
                .header("Content-Encoding", "gzip")
                .method(originalRequest.method(), gzip(body))
                .build();
        return chain.proceed(compressedRequest);
    }

    private RequestBody gzip(final RequestBody body) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return body.contentType();
            }
            @Override
            public long contentLength() {
                return -1;
            }
            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                BufferedSink gzipSink = Okio.buffer(new GzipSink(sink));
                body.writeTo(gzipSink);
                gzipSink.close();
            }
        };
    }
}
