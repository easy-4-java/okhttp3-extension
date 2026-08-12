package okhttp3.extension.logging;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SafeHttpLoggingInterceptorTests {

    @Test
    void shouldOrderLevelsLikeOkHttpLoggingInterceptor() {
        assertFalse(HttpLogLevel.NONE.allows(HttpLogLevel.BASIC));
        assertTrue(HttpLogLevel.BASIC.allows(HttpLogLevel.BASIC));
        assertTrue(HttpLogLevel.HEADERS.allows(HttpLogLevel.BASIC));
        assertTrue(HttpLogLevel.BODY.allows(HttpLogLevel.HEADERS));
    }

    @Test
    void shouldSkipLoggingWhenLevelIsNone() throws Exception {
        Logger logger = mock(Logger.class);
        Request request = request();
        SafeHttpLoggingInterceptor interceptor = new SafeHttpLoggingInterceptor(logger, HttpLogLevel.NONE, 16);

        Response response = interceptor.intercept(new StaticChain(request, response(request)));

        assertEquals("response-secret", response.body().string());
        verify(logger, never()).debug(any(String.class), any(Object[].class));
    }

    @Test
    void shouldRedactHeadersTruncateBodiesAndPreserveResponse() throws Exception {
        Logger logger = mock(Logger.class);
        when(logger.isDebugEnabled()).thenReturn(true);
        Request request = request();
        SafeHttpLoggingInterceptor interceptor = new SafeHttpLoggingInterceptor(logger, HttpLogLevel.BODY, 8);

        Response response = interceptor.intercept(new StaticChain(request, response(request)));

        assertEquals("response-secret", response.body().string());
        verify(logger).debug(eq("HTTP request headers: requestId={}, headers={}"), any(), eq("{Authorization=<redacted>}"));
        verify(logger).debug(eq("HTTP request body: requestId={}, body={}"), any(), eq("request-...<truncated>"));
        verify(logger).debug(eq("HTTP response body: requestId={}, body={}"), any(), eq("response...<truncated>"));
    }

    @Test
    void shouldLogAndRethrowNetworkFailure() {
        Logger logger = mock(Logger.class);
        when(logger.isDebugEnabled()).thenReturn(true);
        Request request = request();
        SafeHttpLoggingInterceptor interceptor = new SafeHttpLoggingInterceptor(logger, HttpLogLevel.BASIC, 8);

        IOException error = assertThrows(IOException.class,
                () -> interceptor.intercept(new FailingChain(request)));

        assertEquals("network down", error.getMessage());
        verify(logger).debug(eq("HTTP request failed: requestId={}, method={}, url={}, elapsedMs={}, error={}"),
                any(), eq("POST"), any(), any(), eq("network down"));
    }

    @Test
    void shouldSupportCustomRedactionAndEmptyBodies() throws Exception {
        Logger logger = mock(Logger.class);
        when(logger.isDebugEnabled()).thenReturn(true);
        Request request = new Request.Builder().url("http://localhost/empty")
                .header("X-Tenant-Secret", "tenant-secret").build();
        Response response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(204).message("No Content").build();
        SafeHttpLoggingInterceptor interceptor = new SafeHttpLoggingInterceptor(logger, HttpLogLevel.BODY, 8)
                .redactHeader("X-Tenant-Secret").redactHeader(" ");

        interceptor.intercept(new StaticChain(request, response));

        verify(logger).debug(eq("HTTP request headers: requestId={}, headers={}"),
                any(), eq("{X-Tenant-Secret=<redacted>}"));
        verify(logger).debug(eq("HTTP request body: requestId={}, body={}"), any(), eq("<empty>"));
        verify(logger).debug(eq("HTTP response body: requestId={}, body={}"), any(), eq("<empty>"));
    }

    @Test
    void shouldOmitBinaryBodies() throws Exception {
        Logger logger = mock(Logger.class);
        when(logger.isDebugEnabled()).thenReturn(true);
        MediaType binary = MediaType.get("application/octet-stream");
        Request request = new Request.Builder().url("http://localhost/binary")
                .post(RequestBody.create(new byte[]{1, 2, 3}, binary)).build();
        Response response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(ResponseBody.create(new byte[]{4, 5}, binary)).build();
        SafeHttpLoggingInterceptor interceptor = new SafeHttpLoggingInterceptor(logger, HttpLogLevel.BODY, 8);

        interceptor.intercept(new StaticChain(request, response));

        verify(logger).debug(eq("HTTP request body: requestId={}, body={}"),
                any(), eq("<binary body omitted>"));
        verify(logger).debug(eq("HTTP response body: requestId={}, body={}"),
                any(), eq("<binary body omitted>"));
    }

    /**
     * Sensitive URL query parameters (such as {@code access_token}) must be redacted in every
     * log line, including the success and failure paths. Non-sensitive parameters are kept
     * verbatim. The configured redaction list can be extended via {@code redactQueryParam}.
     */
    @Test
    void shouldRedactSensitiveQueryParametersFromLogs() throws Exception {
        Logger logger = mock(Logger.class);
        when(logger.isDebugEnabled()).thenReturn(true);
        Request request = new Request.Builder()
                .url("http://localhost/debug?access_token=SECRET&scope=public&token=NOPE&safe=ok")
                .post(RequestBody.create("{}", MediaType.get("application/json")))
                .build();
        Response response = response(request);
        SafeHttpLoggingInterceptor interceptor = new SafeHttpLoggingInterceptor(logger, HttpLogLevel.BASIC, 8)
                .redactQueryParam("SAFE"); // covers the API path: lowercases + adds to set

        interceptor.intercept(new StaticChain(request, response));

        // OkHttp canonicalizes query parameters (alphabetical by name). All sensitive keys
        // (access_token, token, the user-added "safe") are redacted in-place; non-sensitive
        // (scope) is preserved.
        verify(logger).debug(eq("HTTP request started: requestId={}, method={}, url={}"),
                any(), eq("POST"),
                eq("http://localhost/debug?scope=public"
                        + "&access_token=__okhttp3_redacted__"
                        + "&token=__okhttp3_redacted__"
                        + "&safe=__okhttp3_redacted__"));
        verify(logger).debug(eq("HTTP request completed: requestId={}, method={}, url={}, status={}, bodyLength={}, elapsedMs={}"),
                any(), eq("POST"),
                eq("http://localhost/debug?scope=public"
                        + "&access_token=__okhttp3_redacted__"
                        + "&token=__okhttp3_redacted__"
                        + "&safe=__okhttp3_redacted__"),
                eq(200), any(), any());
    }

    @Test
    void shouldRedactQueryParametersInFailurePath() {
        Logger logger = mock(Logger.class);
        when(logger.isDebugEnabled()).thenReturn(true);
        Request request = new Request.Builder()
                .url("http://localhost/fail?api_key=SECRET")
                .post(RequestBody.create("{}", MediaType.get("application/json")))
                .build();
        SafeHttpLoggingInterceptor interceptor = new SafeHttpLoggingInterceptor(logger, HttpLogLevel.BASIC, 8);

        assertThrows(IOException.class, () -> interceptor.intercept(new FailingChain(request)));

        verify(logger).debug(eq("HTTP request failed: requestId={}, method={}, url={}, elapsedMs={}, error={}"),
                any(), eq("POST"), eq("http://localhost/fail?api_key=__okhttp3_redacted__"),
                any(), eq("network down"));
    }

    /**
     * The request-body reader must short-circuit on declared sizes that exceed the configured
     * log budget so that callers cannot accidentally force {@link Buffer#writeTo} to allocate
     * arbitrarily large arrays when BODY level logging is enabled.
     */
    @Test
    void shouldOmitRequestBodyWhenDeclaredSizeExceedsLimit() throws Exception {
        Logger logger = mock(Logger.class);
        when(logger.isDebugEnabled()).thenReturn(true);
        byte[] large = new byte[64];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) i;
        }
        Request request = new Request.Builder().url("http://localhost/large")
                .post(RequestBody.create(large, MediaType.get("application/json")))
                .build();
        Response response = response(request);
        // maxContentLength = 8 → cap = 32 bytes; declared 64 must trigger the guard.
        SafeHttpLoggingInterceptor interceptor = new SafeHttpLoggingInterceptor(logger, HttpLogLevel.BODY, 8);

        interceptor.intercept(new StaticChain(request, response));

        verify(logger).debug(eq("HTTP request body: requestId={}, body={}"), any(),
                argThat(value -> value != null
                        && value.toString().startsWith("<request body omitted:")
                        && value.toString().contains("64 bytes")));
    }

    private Request request() {
        return new Request.Builder().url("http://localhost/debug")
                .header("Authorization", "Bearer secret")
                .post(RequestBody.create("request-secret", MediaType.get("application/json")))
                .build();
    }

    private Response response(Request request) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "application/json")
                .body(ResponseBody.create("response-secret", MediaType.get("application/json")))
                .build();
    }

private static final class StaticChain implements Interceptor.Chain {
        private final Request request;
        private final Response response;

        private StaticChain(Request request, Response response) {
            this.request = request;
            this.response = response;
        }

        @Override public Request request() { return request; }
        @Override public Response proceed(Request request) throws IOException { return response; }
        @Override public okhttp3.Connection connection() { return null; }
        @Override public int connectTimeoutMillis() { return 1_000; }
        @Override public Interceptor.Chain withConnectTimeout(int timeout, java.util.concurrent.TimeUnit unit) { return this; }
        @Override public int readTimeoutMillis() { return 1_000; }
        @Override public Interceptor.Chain withReadTimeout(int timeout, java.util.concurrent.TimeUnit unit) { return this; }
        @Override public int writeTimeoutMillis() { return 1_000; }
        @Override public Interceptor.Chain withWriteTimeout(int timeout, java.util.concurrent.TimeUnit unit) { return this; }
        @Override public okhttp3.Call call() { return mock(okhttp3.Call.class); }
    }

    private static final class FailingChain implements Interceptor.Chain {
        private final Request request;

        private FailingChain(Request request) {
            this.request = request;
        }

        @Override public Request request() { return request; }
        @Override public Response proceed(Request request) throws IOException { throw new IOException("network down"); }
        @Override public okhttp3.Connection connection() { return null; }
        @Override public int connectTimeoutMillis() { return 1_000; }
        @Override public Interceptor.Chain withConnectTimeout(int timeout, java.util.concurrent.TimeUnit unit) { return this; }
        @Override public int readTimeoutMillis() { return 1_000; }
        @Override public Interceptor.Chain withReadTimeout(int timeout, java.util.concurrent.TimeUnit unit) { return this; }
        @Override public int writeTimeoutMillis() { return 1_000; }
        @Override public Interceptor.Chain withWriteTimeout(int timeout, java.util.concurrent.TimeUnit unit) { return this; }
        @Override public okhttp3.Call call() { return mock(okhttp3.Call.class); }
    }
}
