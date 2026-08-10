/*
 * Copyright (c) 2018, Loong Wan (https://github.com/loong10k).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package okhttp3.extension.cache;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 *  持久化Cookie，运行时缓存了Cookie，当App退出的时候Cookie就不存在了
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
public class PersistenceCookieJar implements CookieJar {
    
	private final ConcurrentMap<CookieKey, Cookie> cache = new ConcurrentHashMap<>();

    /*
     * Http请求结束，Response中有Cookie时候回调
     */
    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        long now = System.currentTimeMillis();
        for (Cookie cookie : cookies) {
            CookieKey key = CookieKey.from(cookie);
            if (cookie.expiresAt() <= now) {
                cache.remove(key);
            } else {
                cache.put(key, cookie);
            }
        }
    }

    /*
     * Http发送请求前回调，Request中设置Cookie
     */
    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        long now = System.currentTimeMillis();
        List<Cookie> validCookies = new java.util.ArrayList<>();
        cache.forEach((key, cookie) -> {
            if (cookie.expiresAt() <= now) {
                cache.remove(key, cookie);
            } else if (cookie.matches(url)) {
                validCookies.add(cookie);
            }
        });
        return validCookies.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(validCookies);
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
