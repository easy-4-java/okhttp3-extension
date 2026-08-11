package okhttp3.extension.cookie;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.CookieJar;
import okhttp3.extension.cache.PersistenceCookieJar;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
        jar.saveFromResponse(API_URL, Collections.singletonList(cookie("root", "1", "/")));
        jar.saveFromResponse(API_URL, Collections.singletonList(cookie("api", "2", "/api")));

        assertEquals(2, jar.loadForRequest(API_URL).size());
        assertEquals(Collections.singletonList("root"),
                jar.loadForRequest(OTHER_URL).stream().map(Cookie::name).collect(Collectors.toList()));
    }

    @Test
    void persistenceJarShouldReplaceCookieWithSameIdentity() {
        PersistenceCookieJar jar = new PersistenceCookieJar();
        jar.saveFromResponse(API_URL, Collections.singletonList(cookie("session", "old", "/")));
        jar.saveFromResponse(API_URL, Collections.singletonList(cookie("session", "new", "/")));

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
        root.saveFromResponse(API_URL, Collections.singletonList(cookie("session", "root", "/")));
        scoped.saveFromResponse(API_URL, Collections.singletonList(cookie("session", "api", "/api")));

        NestedCookieJar jar = new NestedCookieJar(Arrays.asList(root, scoped));
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
        doThrow(new IllegalStateException("save failed")).when(failing)
                .saveFromResponse(API_URL, Collections.singletonList(root));
        when(failing.loadForRequest(API_URL)).thenThrow(new IllegalStateException("load failed"));
        when(empty.loadForRequest(API_URL)).thenReturn(null);
        when(values.loadForRequest(API_URL)).thenReturn(Arrays.asList(root, replacement, mismatched));

        NestedCookieJar jar = new NestedCookieJar(Arrays.asList(failing, empty, values));
        jar.saveFromResponse(API_URL, Collections.singletonList(root));
        List<Cookie> loaded = jar.loadForRequest(API_URL);
        assertEquals(1, loaded.size());
        assertEquals("replacement", loaded.get(0).value());
        assertTrue(new NestedCookieJar(null).loadForRequest(API_URL).isEmpty());
        new NestedCookieJar(null).saveFromResponse(API_URL, Collections.singletonList(root));
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
