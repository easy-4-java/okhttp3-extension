package okhttp3.extension.interceptor;

import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestInterceptorTests {

    @Test
    void shouldAddOnlyMissingNonBlankHeadersAndToggle() throws Exception {
        RequestHeaderInterceptor interceptor = new RequestHeaderInterceptor(() -> java.util.Arrays.asList(
                new RequestHeaderInterceptor.HeaderEntry("Accept", "application/json"),
                new RequestHeaderInterceptor.HeaderEntry("Existing", "new"),
                new RequestHeaderInterceptor.HeaderEntry("Blank", " "),
                new RequestHeaderInterceptor.HeaderEntry("Null", null),
                null));
        Request request = new Request.Builder().url("http://localhost").header("Existing", "old").build();
        CapturingChain chain = new CapturingChain(request);

        interceptor.intercept(chain).close();
        assertEquals("application/json", chain.proceeded.header("Accept"));
        assertEquals("old", chain.proceeded.header("Existing"));
        assertNull(chain.proceeded.header("Blank"));
        assertTrue(interceptor.isEnabled());
        interceptor.disable();
        assertFalse(interceptor.isEnabled());
        interceptor.intercept(chain).close();
        interceptor.enable();
        assertTrue(interceptor.isEnabled());

        RequestHeaderInterceptor empty = new RequestHeaderInterceptor(() -> null);
        empty.intercept(new CapturingChain(request)).close();
        assertThrows(NullPointerException.class, () -> new RequestHeaderInterceptor(null));
        RequestHeaderInterceptor.HeaderEntry entry = new RequestHeaderInterceptor.HeaderEntry("A", "B");
        assertEquals("A", entry.getName());
        assertEquals("B", entry.getValue());
    }

    @Test
    void shouldCompressEligibleBodiesAndSkipIneligibleBodies() throws Exception {
        String large = "x".repeat(2_048);
        Request request = new Request.Builder().url("http://localhost")
                .post(RequestBody.create(large, MediaType.get("text/plain"))).build();
        GzipRequestInterceptor interceptor = new GzipRequestInterceptor(true, 100);
        CapturingChain chain = new CapturingChain(request);

        interceptor.intercept(chain).close();
        assertEquals("gzip", chain.proceeded.header("Content-Encoding"));
        assertEquals(-1L, chain.proceeded.body().contentLength());
        assertEquals(request.body().contentType(), chain.proceeded.body().contentType());
        Buffer compressed = new Buffer();
        chain.proceeded.body().writeTo(compressed);
        assertTrue(compressed.size() < large.length());

        interceptor.disable();
        interceptor.intercept(new CapturingChain(request)).close();
        interceptor.enable();
        assertTrue(interceptor.isEnabled());
        Request small = request.newBuilder().post(RequestBody.create("small", MediaType.get("text/plain"))).build();
        CapturingChain smallChain = new CapturingChain(small);
        interceptor.intercept(smallChain).close();
        assertNull(smallChain.proceeded.header("Content-Encoding"));
        Request encoded = request.newBuilder().header("Content-Encoding", "br").build();
        interceptor.intercept(new CapturingChain(encoded)).close();
        Request noBody = new Request.Builder().url("http://localhost").get().build();
        interceptor.intercept(new CapturingChain(noBody)).close();
    }

    @Test
    void shouldHandleRetryCancellationIoFailureAndNonReplayableBody() throws Exception {
        Request get = new Request.Builder().url("http://localhost").get().build();
        Interceptor.Chain cancelled = mock(Interceptor.Chain.class);
        Call call = mock(Call.class);
        when(cancelled.request()).thenReturn(get);
        when(cancelled.call()).thenReturn(call);
        when(call.isCanceled()).thenReturn(true);
        assertThrows(java.io.InterruptedIOException.class,
                () -> new RequestRetryIntercepter(1, 0).intercept(cancelled));

        Interceptor.Chain failing = mock(Interceptor.Chain.class);
        when(failing.request()).thenReturn(get);
        when(failing.call()).thenReturn(call);
        when(call.isCanceled()).thenReturn(false);
        when(failing.proceed(any())).thenThrow(new IOException("first"), new IOException("last"));
        assertThrows(IOException.class, () -> new RequestRetryIntercepter(1, 0).intercept(failing));

        RequestBody oneShot = new RequestBody() {
            @Override public MediaType contentType() { return MediaType.get("text/plain"); }
            @Override public void writeTo(okio.BufferedSink sink) throws IOException { sink.writeUtf8("x"); }
            @Override public boolean isOneShot() { return true; }
        };
        Request post = new Request.Builder().url("http://localhost").header("Idempotency-Key", "key").post(oneShot).build();
        CapturingChain postChain = new CapturingChain(post);
        new RequestRetryIntercepter(1, 0).intercept(postChain).close();
        assertEquals(1, postChain.calls);
        assertEquals(0L, new RequestRetryIntercepter(-1, -1).getRetryInterval());
        assertFalse(new RequestRetryIntercepter(0, 0).isEnabled());
        assertNull(ProxyAuthenticator.NONE.authenticate(null, response(get)));
    }

    private static Response response(Request request) {
        return new Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body(ResponseBody.create("ok", MediaType.get("text/plain"))).build();
    }

    private static final class CapturingChain implements Interceptor.Chain {
        private final Request original;
        private Request proceeded;
        private int calls;
        private CapturingChain(Request original) { this.original = original; }
        @Override public Request request() { return original; }
        @Override public Response proceed(Request request) { proceeded = request; calls++; return response(request); }
        @Override public okhttp3.Connection connection() { return null; }
        @Override public Call call() { return mock(Call.class); }
        @Override public int connectTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withConnectTimeout(int timeout, java.util.concurrent.TimeUnit unit) { return this; }
        @Override public int readTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withReadTimeout(int timeout, java.util.concurrent.TimeUnit unit) { return this; }
        @Override public int writeTimeoutMillis() { return 0; }
        @Override public Interceptor.Chain withWriteTimeout(int timeout, java.util.concurrent.TimeUnit unit) { return this; }
        public boolean getFollowSslRedirects() { return true; }
        public boolean getFollowRedirects() { return true; }
        public okhttp3.Dns getDns() { return okhttp3.Dns.SYSTEM; }
        public Interceptor.Chain withDns(okhttp3.Dns dns) { return this; }
        public javax.net.SocketFactory getSocketFactory() { return javax.net.SocketFactory.getDefault(); }
        public Interceptor.Chain withSocketFactory(javax.net.SocketFactory factory) { return this; }
        public boolean getRetryOnConnectionFailure() { return true; }
        public Interceptor.Chain withRetryOnConnectionFailure(boolean value) { return this; }
        public okhttp3.Authenticator getAuthenticator() { return okhttp3.Authenticator.NONE; }
        public Interceptor.Chain withAuthenticator(okhttp3.Authenticator authenticator) { return this; }
        public okhttp3.CookieJar getCookieJar() { return okhttp3.CookieJar.NO_COOKIES; }
        public Interceptor.Chain withCookieJar(okhttp3.CookieJar cookieJar) { return this; }
        public okhttp3.Cache getCache() { return null; }
        public Interceptor.Chain withCache(okhttp3.Cache cache) { return this; }
        public java.net.Proxy getProxy() { return null; }
        public Interceptor.Chain withProxy(java.net.Proxy proxy) { return this; }
        public java.net.ProxySelector getProxySelector() { return java.net.ProxySelector.getDefault(); }
        public Interceptor.Chain withProxySelector(java.net.ProxySelector selector) { return this; }
        public okhttp3.Authenticator getProxyAuthenticator() { return okhttp3.Authenticator.NONE; }
        public Interceptor.Chain withProxyAuthenticator(okhttp3.Authenticator authenticator) { return this; }
        public javax.net.ssl.SSLSocketFactory getSslSocketFactoryOrNull() { return null; }
        public Interceptor.Chain withSslSocketFactory(javax.net.ssl.SSLSocketFactory factory, javax.net.ssl.X509TrustManager manager) { return this; }
        public javax.net.ssl.X509TrustManager getX509TrustManagerOrNull() { return null; }
        public javax.net.ssl.HostnameVerifier getHostnameVerifier() { return (h, s) -> true; }
        public Interceptor.Chain withHostnameVerifier(javax.net.ssl.HostnameVerifier verifier) { return this; }
        public okhttp3.CertificatePinner getCertificatePinner() { return okhttp3.CertificatePinner.DEFAULT; }
        public Interceptor.Chain withCertificatePinner(okhttp3.CertificatePinner pinner) { return this; }
        public okhttp3.ConnectionPool getConnectionPool() { return new okhttp3.ConnectionPool(); }
        public Interceptor.Chain withConnectionPool(okhttp3.ConnectionPool pool) { return this; }
        public okhttp3.EventListener getEventListener() { return okhttp3.EventListener.NONE; }
    }
}
