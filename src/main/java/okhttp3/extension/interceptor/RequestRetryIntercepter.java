package okhttp3.extension.interceptor;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * OkHttp interceptor that retries failed requests.
 */
@Slf4j
public class RequestRetryIntercepter implements RequestInterceptor {

    private static final long MAX_RETRY_INTERVAL_MILLIS = 30_000L;
    private static final Set<String> IDEMPOTENT_METHODS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("GET", "HEAD", "OPTIONS", "TRACE")));
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(408, 425, 429, 500, 502, 503, 504)));

    private final int retryMaxAttempts;
    private final long retryInterval;
    private final boolean enabled;

    public RequestRetryIntercepter(int retryMaxAttempts, long retryInterval) {
        this.retryMaxAttempts = Math.max(0, retryMaxAttempts);
        this.retryInterval = Math.max(0L, retryInterval);
        this.enabled = this.retryMaxAttempts > 0;
    }

    @SuppressWarnings("resource")
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (!enabled || !isRetryableRequest(request)) {
            return chain.proceed(request);
        }

        IOException lastFailure = null;
        for (int attempt = 0; attempt <= retryMaxAttempts; attempt++) {
            if (chain.call().isCanceled()) {
                throw new InterruptedIOException("Call was canceled");
            }

            Response response = null;
            try {
                response = chain.proceed(request);
                if (!isRetryableResponse(response) || attempt == retryMaxAttempts) {
                    return response;
                }
            } catch (IOException e) {
                lastFailure = e;
                if (attempt == retryMaxAttempts) {
                    throw e;
                }
            } finally {
                if (response != null && isRetryableResponse(response) && attempt < retryMaxAttempts) {
                    response.close();
                }
            }

            int retryNumber = attempt + 1;
            log.warn("Retrying HTTP request: method={}, url={}, retry={}/{}",
                    request.method(), request.url(), retryNumber, retryMaxAttempts);
            waitBeforeRetry(retryNumber);
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IOException("HTTP retry loop completed without a response");
    }

    private boolean isRetryableRequest(Request request) {
        if (IDEMPOTENT_METHODS.contains(request.method())) {
            return true;
        }
        String idempotencyKey = request.header("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return false;
        }
        RequestBody body = request.body();
        return body == null || (!body.isOneShot() && !body.isDuplex());
    }

    private boolean isRetryableResponse(Response response) {
        return RETRYABLE_STATUS_CODES.contains(response.code());
    }

    private void waitBeforeRetry(int retryNumber) throws InterruptedIOException {
        if (retryInterval <= 0L) {
            return;
        }
        int shift = Math.min(retryNumber - 1, 20);
        long multiplier = 1L << shift;
        long delay;
        try {
            delay = Math.multiplyExact(retryInterval, multiplier);
        } catch (ArithmeticException e) {
            delay = MAX_RETRY_INTERVAL_MILLIS;
        }
        delay = Math.min(delay, MAX_RETRY_INTERVAL_MILLIS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException("HTTP retry interrupted");
            interrupted.initCause(e);
            throw interrupted;
        }
    }

    public long getRetryInterval() {
        return this.retryInterval;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
