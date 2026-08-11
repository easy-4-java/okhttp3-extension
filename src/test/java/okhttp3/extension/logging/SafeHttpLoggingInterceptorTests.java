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
        verify(logger).debug(eq("HTTP response body: requestId={}, body={}"), any(), eq(""));
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

    private static class StaticChain implements Interceptor.Chain {
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
        @Override public boolean getFollowSslRedirects() { return true; }
        @Override public boolean getFollowRedirects() { return true; }
        @Override public okhttp3.Dns getDns() { return okhttp3.Dns.SYSTEM; }
        @Override public Interceptor.Chain withDns(okhttp3.Dns dns) { return this; }
        @Override public javax.net.SocketFactory getSocketFactory() { return javax.net.SocketFactory.getDefault(); }
        @Override public Interceptor.Chain withSocketFactory(javax.net.SocketFactory factory) { return this; }
        @Override public boolean getRetryOnConnectionFailure() { return true; }
        @Override public Interceptor.Chain withRetryOnConnectionFailure(boolean value) { return this; }
        @Override public okhttp3.Authenticator getAuthenticator() { return okhttp3.Authenticator.NONE; }
        @Override public Interceptor.Chain withAuthenticator(okhttp3.Authenticator authenticator) { return this; }
        @Override public okhttp3.CookieJar getCookieJar() { return okhttp3.CookieJar.NO_COOKIES; }
        @Override public Interceptor.Chain withCookieJar(okhttp3.CookieJar cookieJar) { return this; }
        @Override public okhttp3.Cache getCache() { return null; }
        @Override public Interceptor.Chain withCache(okhttp3.Cache cache) { return this; }
        @Override public java.net.Proxy getProxy() { return null; }
        @Override public Interceptor.Chain withProxy(java.net.Proxy proxy) { return this; }
        @Override public java.net.ProxySelector getProxySelector() { return java.net.ProxySelector.getDefault(); }
        @Override public Interceptor.Chain withProxySelector(java.net.ProxySelector selector) { return this; }
        @Override public okhttp3.Authenticator getProxyAuthenticator() { return okhttp3.Authenticator.NONE; }
        @Override public Interceptor.Chain withProxyAuthenticator(okhttp3.Authenticator authenticator) { return this; }
        @Override public javax.net.ssl.SSLSocketFactory getSslSocketFactoryOrNull() { return null; }
        @Override public Interceptor.Chain withSslSocketFactory(javax.net.ssl.SSLSocketFactory factory, javax.net.ssl.X509TrustManager manager) { return this; }
        @Override public javax.net.ssl.X509TrustManager getX509TrustManagerOrNull() { return null; }
        @Override public javax.net.ssl.HostnameVerifier getHostnameVerifier() { return (host, session) -> true; }
        @Override public Interceptor.Chain withHostnameVerifier(javax.net.ssl.HostnameVerifier verifier) { return this; }
        @Override public okhttp3.CertificatePinner getCertificatePinner() { return okhttp3.CertificatePinner.DEFAULT; }
        @Override public Interceptor.Chain withCertificatePinner(okhttp3.CertificatePinner pinner) { return this; }
        @Override public okhttp3.ConnectionPool getConnectionPool() { return new okhttp3.ConnectionPool(); }
        @Override public Interceptor.Chain withConnectionPool(okhttp3.ConnectionPool pool) { return this; }
        @Override public okhttp3.EventListener getEventListener() { return okhttp3.EventListener.NONE; }
    }

    private static final class FailingChain extends StaticChain {
        private FailingChain(Request request) { super(request, null); }
        @Override public Response proceed(Request request) throws IOException { throw new IOException("network down"); }
    }
}
