package okhttp3.extension.cookie;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link CookieJar} that delegates to multiple cookie jars.
 */
@Slf4j
public class NestedCookieJar implements CookieJar {

    private final List<CookieJar> cookieJars;

    public NestedCookieJar(List<CookieJar> cookieJars) {
        this.cookieJars = cookieJars == null ? Collections.emptyList() : Collections.unmodifiableList(cookieJars);
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        if (cookieJars == null || cookieJars.isEmpty()) {
            return;
        }
        for (CookieJar cookieJar : cookieJars) {
            try {
                cookieJar.saveFromResponse(url, cookies);
            } catch (Exception e) {
                log.error("saveFromResponse error", e);
            }
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        if (cookieJars == null || cookieJars.isEmpty()) {
            return Collections.emptyList();
        }
        Map<CookieKey, Cookie> cookieMap = new LinkedHashMap<>();
        for (CookieJar cookieJar : cookieJars) {
            try {
                List<Cookie> cookies = cookieJar.loadForRequest(url);
                if (cookies == null || cookies.isEmpty()) {
                    continue;
                }
                for (Cookie cookie : cookies) {
                    if (cookie.matches(url) && cookie.expiresAt() >= System.currentTimeMillis()) {
                        cookieMap.put(CookieKey.from(cookie), cookie);
                    }
                }
            } catch (Exception e) {
                log.error("loadForRequest error", e);
            }
        }
        if (cookieMap.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(cookieMap.values()));
    }

    private static final class CookieKey {
        private final String name;
        private final String domain;
        private final String path;

        private CookieKey(String name, String domain, String path) {
            this.name = name;
            this.domain = domain;
            this.path = path;
        }

        private static CookieKey from(Cookie cookie) {
            return new CookieKey(cookie.name(), cookie.domain(), cookie.path());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CookieKey)) return false;
            CookieKey that = (CookieKey) o;
            return java.util.Objects.equals(name, that.name)
                && java.util.Objects.equals(domain, that.domain)
                && java.util.Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, domain, path);
        }
    }
}
