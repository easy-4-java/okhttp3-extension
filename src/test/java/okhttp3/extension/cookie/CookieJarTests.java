package okhttp3.extension.cookie;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.CookieJar;
import okhttp3.extension.cache.PersistenceCookieJar;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cookie 标识、覆盖与 URL 匹配测试。
 */
class CookieJarTests {

    private static final HttpUrl API_URL = HttpUrl.get("https://example.com/api/chat");
    private static final HttpUrl OTHER_URL = HttpUrl.get("https://example.com/other");

    @Test
    void caffeineJarShouldMergeResponsesAndRespectCookiePath() {
        CaffeineCacheCookieJar jar = new CaffeineCacheCookieJar(
                100, Duration.ofHours(1), Duration.ofHours(1));
        jar.saveFromResponse(API_URL, List.of(cookie("root", "1", "/")));
        jar.saveFromResponse(API_URL, List.of(cookie("api", "2", "/api")));

        assertEquals(2, jar.loadForRequest(API_URL).size());
        assertEquals(List.of("root"), jar.loadForRequest(OTHER_URL).stream().map(Cookie::name).toList());
    }

    @Test
    void persistenceJarShouldReplaceCookieWithSameIdentity() {
        PersistenceCookieJar jar = new PersistenceCookieJar();
        jar.saveFromResponse(API_URL, List.of(cookie("session", "old", "/")));
        jar.saveFromResponse(API_URL, List.of(cookie("session", "new", "/")));

        List<Cookie> cookies = jar.loadForRequest(API_URL);
        assertEquals(1, cookies.size());
        assertEquals("new", cookies.get(0).value());
    }

    @Test
    void nestedJarShouldRetainSameNameCookiesWithDifferentPaths() {
        CaffeineCacheCookieJar root = new CaffeineCacheCookieJar(
                100, Duration.ofHours(1), Duration.ofHours(1));
        CaffeineCacheCookieJar scoped = new CaffeineCacheCookieJar(
                100, Duration.ofHours(1), Duration.ofHours(1));
        root.saveFromResponse(API_URL, List.of(cookie("session", "root", "/")));
        scoped.saveFromResponse(API_URL, List.of(cookie("session", "api", "/api")));

        NestedCookieJar jar = new NestedCookieJar(List.of(root, scoped));
        assertEquals(2, jar.loadForRequest(API_URL).size());
    }

    @Test
    void nestedJarShouldIsolateDelegateFailuresAndFilterInvalidCookies() {
        CookieJar failing = mock(CookieJar.class);
        CookieJar empty = mock(CookieJar.class);
        CookieJar values = mock(CookieJar.class);
        Cookie root = cookie("session", "root", "/");
        Cookie replacement = cookie("session", "replacement", "/");
        Cookie mismatched = cookie("other", "value", "/other");
        doThrow(new IllegalStateException("save failed")).when(failing).saveFromResponse(API_URL, List.of(root));
        when(failing.loadForRequest(API_URL)).thenThrow(new IllegalStateException("load failed"));
        when(empty.loadForRequest(API_URL)).thenReturn(null);
        when(values.loadForRequest(API_URL)).thenReturn(List.of(root, replacement, mismatched));

        NestedCookieJar jar = new NestedCookieJar(List.of(failing, empty, values));
        jar.saveFromResponse(API_URL, List.of(root));
        List<Cookie> loaded = jar.loadForRequest(API_URL);
        assertEquals(1, loaded.size());
        assertEquals("replacement", loaded.get(0).value());
        assertTrue(new NestedCookieJar(null).loadForRequest(API_URL).isEmpty());
        new NestedCookieJar(null).saveFromResponse(API_URL, List.of(root));
    }

    private static Cookie cookie(String name, String value, String path) {
        return new Cookie.Builder()
                .name(name)
                .value(value)
                .domain("example.com")
                .path(path)
                .expiresAt(System.currentTimeMillis() + Duration.ofHours(1).toMillis())
                .build();
    }
}
