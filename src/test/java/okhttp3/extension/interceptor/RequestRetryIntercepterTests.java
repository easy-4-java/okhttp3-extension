package okhttp3.extension.interceptor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 请求重试策略测试。
 */
class RequestRetryIntercepterTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldRetryIdempotentGetAndReleasePreviousResponse() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        server = startServer(exchange -> {
            int count = requestCount.incrementAndGet();
            respond(exchange, count == 1 ? 503 : 200, count == 1 ? "unavailable" : "ok");
        });
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new RequestRetryIntercepter(1, 0L))
                .build();
        Request request = new Request.Builder().url(baseUrl()).get().build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
            assertEquals("ok", response.body().string());
        } finally {
            shutdown(client);
        }
        assertEquals(2, requestCount.get());
    }

    @Test
    void shouldNotRetryPostWithoutIdempotencyKey() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        server = startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 503, "unavailable");
        });
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new RequestRetryIntercepter(3, 0L))
                .build();
        Request request = new Request.Builder()
                .url(baseUrl())
                .post(RequestBody.create("{}", MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(503, response.code());
        } finally {
            shutdown(client);
        }
        assertEquals(1, requestCount.get());
    }

    @Test
    void shouldRetryReplayablePostWithIdempotencyKey() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        server = startServer(exchange -> {
            int count = requestCount.incrementAndGet();
            respond(exchange, count == 1 ? 503 : 200, "result");
        });
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new RequestRetryIntercepter(1, 0L))
                .build();
        Request request = new Request.Builder()
                .url(baseUrl())
                .header("Idempotency-Key", "request-1")
                .post(RequestBody.create("{}", MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
        } finally {
            shutdown(client);
        }
        assertEquals(2, requestCount.get());
    }

    @Test
    void shouldRetryTransportFailureAndPropagateLastFailure() throws Exception {
        okhttp3.Interceptor.Chain chain = mock(okhttp3.Interceptor.Chain.class);
        okhttp3.Call call = mock(okhttp3.Call.class);
        Request request = new Request.Builder().url("http://localhost/").get().build();
        AtomicInteger attempts = new AtomicInteger();
        when(chain.request()).thenReturn(request);
        when(chain.call()).thenReturn(call);
        when(call.isCanceled()).thenReturn(false);
        when(chain.proceed(request)).thenAnswer(ignored -> {
            int attempt = attempts.incrementAndGet();
            throw new IOException("failure-" + attempt);
        });
        RequestRetryIntercepter interceptor = new RequestRetryIntercepter(1, 0L);

        IOException error = assertThrows(IOException.class, () -> interceptor.intercept(chain));
        assertEquals("failure-2", error.getMessage());
        assertEquals(2, attempts.get());
    }

    @Test
    void shouldStopWhenCallIsCancelledOrRetrySleepIsInterrupted() throws Exception {
        okhttp3.Interceptor.Chain cancelled = mock(okhttp3.Interceptor.Chain.class);
        okhttp3.Call call = mock(okhttp3.Call.class);
        Request request = new Request.Builder().url("http://localhost/").get().build();
        when(cancelled.request()).thenReturn(request);
        when(cancelled.call()).thenReturn(call);
        when(call.isCanceled()).thenReturn(true);
        assertThrows(InterruptedIOException.class,
                () -> new RequestRetryIntercepter(1, 0L).intercept(cancelled));

        okhttp3.Interceptor.Chain interrupted = mock(okhttp3.Interceptor.Chain.class);
        okhttp3.Call activeCall = mock(okhttp3.Call.class);
        when(interrupted.request()).thenReturn(request);
        when(interrupted.call()).thenReturn(activeCall);
        when(activeCall.isCanceled()).thenReturn(false);
        when(interrupted.proceed(request)).thenReturn(response(request, 503));
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class,
                    () -> new RequestRetryIntercepter(1, Long.MAX_VALUE).intercept(interrupted));
        } finally {
            Thread.interrupted();
        }
    }

    private static Response response(Request request, int code) {
        return new Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code).message("status").body(okhttp3.ResponseBody.create("body", null)).build();
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", handler);
        httpServer.start();
        return httpServer;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void shutdown(OkHttpClient client) {
        client.dispatcher().cancelAll();
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdown();
    }
}
