package okhttp3.extension.cookie;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A {@link CookieJar} backed by Caffeine cache.
 */
@Slf4j
public class CaffeineCacheCookieJar implements CookieJar {

    protected final Cache<CookieKey, Cookie> cookieCache;

    public CaffeineCacheCookieJar(long maximumSize, Duration expireAfterWrite, Duration expireAfterAccess) {
        this.cookieCache = Caffeine.newBuilder()
                .initialCapacity(10)
                .maximumSize(maximumSize)
                .removalListener((RemovalListener<CookieKey, Cookie>) (key, value, cause) -> log.debug("Remove Cookie Cache: key={}, cause={}", key, cause))
                .expireAfterWrite(expireAfterWrite)
                .expireAfterAccess(expireAfterAccess)
                .build();
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        long now = System.currentTimeMillis();
        for (Cookie cookie : cookies) {
            CookieKey key = CookieKey.from(cookie);
            if (cookie.expiresAt() <= now) {
                cookieCache.invalidate(key);
            } else {
                cookieCache.put(key, cookie);
            }
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        long now = System.currentTimeMillis();
        List<Cookie> cookies = new ArrayList<>();
        cookieCache.asMap().forEach((key, cookie) -> {
            if (cookie.expiresAt() <= now) {
                cookieCache.invalidate(key);
            } else if (cookie.matches(url)) {
                cookies.add(cookie);
            }
        });
        return cookies.isEmpty() ? Collections.emptyList() : List.copyOf(cookies);
    }

    private record CookieKey(String name, String domain, String path) {
        private static CookieKey from(Cookie cookie) {
            return new CookieKey(cookie.name(), cookie.domain(), cookie.path());
        }
    }
}
