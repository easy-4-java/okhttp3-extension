# okhttp3-extension

[English](./README.md) | [简体中文](./README.zh-CN.md)

Pure Java extension layer for OkHttp 3/4: SSL utilities, CookieJars, interceptors, response helpers
[简体中文](./README.zh-CN.md)

> **Current branch**: `feature/3.0.x`
> **Version**: `3.0.x.x.20260630-SNAPSHOT`
> **JDK baseline**: 8
> **Project status**: maintenance (1.0.x line). Not yet published to Maven Central; artifacts are distributed via the Aliyun Maven repository and GitHub Releases.

## Table of Contents

- [1. Project Overview](#1-project-overview)
- [2. Features & Status](#2-features--status)
- [3. Requirements & Compatibility](#3-requirements--compatibility)
- [4. Architecture & Modules](#4-architecture--modules)
- [5. Installation](#5-installation)
- [6. Quick Start](#6-quick-start)
- [7. Configuration](#7-configuration)
- [8. Core Usage](#8-core-usage)
- [9. Testing & Build](#9-testing--build)
- [10. Versioning & Branches](#10-versioning--branches)
- [11. Contributing & License](#11-contributing--license)

## 1. Project Overview

### 1.1 What it is

**okhttp3-extension** is a pure Java extension layer for OkHttp. It provides SSL/TLS building blocks, cookie storage strategies, request/response interceptors and small response helpers on top of the official OkHttp API — with **no Spring Boot auto-configuration** inside this module.

### 1.2 What it is not

- **Not a Spring Boot starter.** Auto-configuration lives in the separate `okhttp3-spring-boot-starter` repository; this module stays framework-free.
- **Not a fork of OkHttp.** It extends the official `okhttp` artifact (4.12.0 in this line).
- **Not a metrics module.** Prometheus/Micrometer instrumentation is a sibling module: [okhttp3-metrics-prometheus](https://github.com/easy-4-java/okhttp3-metrics-prometheus).

### 1.3 Typical scenarios

| Scenario | Recommended entry | Result |
|---|---|---|
| Trust-all / custom-trust TLS clients for dev or controlled environments | `SSLContexts.custom()`, `TrustManagerUtils`, `SSLContextBuilder` | Ready-to-use `SSLContext` for `OkHttpClient` |
| Client certificates (mutual TLS) | `SSLContextBuilder.loadKeyMaterial(...)` | mTLS-capable `SSLContext` |
| Persistent cookies across restarts | `PersistenceCookieJar` | Cookie state survives process restarts |
| Expiring, size-bounded in-memory cookies | `CaffeineCacheCookieJar` | TTL/access-expiry cookie cache |
| Multiple cookie sources at once | `NestedCookieJar` | Fan-out save / merge load |
| Global header injection | `RequestHeaderInterceptor` | Headers added to every request |
| Retry on failure | `RequestRetryIntercepter` | Configurable attempts and interval |
| GZIP request bodies | `GzipRequestInterceptor` | Compressed uploads |

<a id="2-features--status"></a>
## 2. Features & Status

| Capability | Status | Notes |
|---|:---:|---|
| SSL context building | Available | `SSLContextBuilder` (fluent), `SSLContexts` (factory), custom `KeyManager` / `TrustManager` strategies |
| Trust-all hostname verifier | Available | `TrustAllHostnameVerifier` |
| Persistent cookie jar | Available | `PersistenceCookieJar` |
| Caffeine-backed cookie jar | Available | `CaffeineCacheCookieJar(maximumSize, expireAfterWrite, expireAfterAccess)` |
| Nested cookie jar | Available | `NestedCookieJar(List<CookieJar>)` |
| Request header interceptor | Available | `RequestHeaderInterceptor` + `RequestHeaderProvider` |
| Retry interceptor | Available | `RequestRetryIntercepter(retryMaxAttempts, retryInterval)` |
| GZIP request interceptor | Available | `GzipRequestInterceptor(enabled)` |
| Response helper | Available | `Okhttp3Response.isSuccess()` |
| Spring Boot auto-configuration | Not included | See the separate `okhttp3-spring-boot-starter` |

<a id="3-requirements--compatibility"></a>
## 3. Requirements & Compatibility

| Component | Version | Notes |
|---|---:|---|
| JDK | 21+ | Enforced by `maven-enforcer-plugin` |
| Maven | 3.0+ | Enforcer minimum |
| OkHttp | 4.12.0 | Via `okhttp-bom` |
| Jackson annotations | 2.17.2 | Via `jackson-bom` |
| Caffeine | 2.9.3 | Cookie cache |
| SLF4J | 2.0.18 | Logging facade |

Version-line matrix:

| Version line | Branch | JDK | Version pattern | Purpose |
|---|---|---:|---|---|
| 1.0.x | `feature/3.0.x` (this branch) | 8 | `1.0.x.*` | For Boot 2.x starters and legacy projects |
| 2.0.x | `feature/2.0.x` | 17 | `2.0.x.*` | For Boot 3.x starters |
| 3.0.x | `feature/3.0.x` | 21 | `3.0.x.*` | For Boot 4.x starters / new projects |

<a id="4-architecture--modules"></a>
## 4. Architecture & Modules

```text
[ Your Application ]
        |
        | OkHttpClient configured with okhttp3-extension
        v
+------------------------------------------+
| SSL        SSLContexts / SSLContextBuilder|
|            TrustManagerUtils / KeyManager |
| Cookie     PersistenceCookieJar /         |
|            CaffeineCacheCookieJar /       |
|            NestedCookieJar                |
| Interceptor Gzip / RequestHeader /        |
|            RequestRetry / Network         |
| Response   Okhttp3Response helpers        |
+------------------------------------------+
        |
        v
[ OkHttp (4.12.0) ] -> [ HTTP(S) endpoints ]
```

Single-module library (packaging `jar`). Package layout:

| Package | Responsibility |
|---|---|
| `okhttp3.extension` | Response helpers (`Okhttp3Response`) |
| `okhttp3.extension.ssl` | SSL/TLS building blocks (`SSLContextBuilder`, `SSLContexts`, trust/key managers, hostname verifiers) |
| `okhttp3.extension.cookie` | In-memory cookie jars (`CaffeineCacheCookieJar`, `NestedCookieJar`) |
| `okhttp3.extension.cache` | Durable cookie jar (`PersistenceCookieJar`) |
| `okhttp3.extension.interceptor` | Interceptors (`GzipRequestInterceptor`, `RequestHeaderInterceptor`, `RequestRetryIntercepter`, `RequestInterceptor` / `NetworkInterceptor` / `ProxyAuthenticator` contracts) |

<a id="5-installation"></a>
## 5. Installation

Maven:

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>okhttp3-extension</artifactId>
    <version>3.0.x.x.20260630-SNAPSHOT</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.easy4j:okhttp3-extension:3.0.x.x.20260630-SNAPSHOT'
```

Snapshot builds require an enabled snapshot repository (Aliyun Maven snapshot repository per `distributionManagement` in `pom.xml`).

<a id="6-quick-start"></a>
## 6. Quick Start

Build a client with a custom-trust SSL context and an expiring cookie jar:

```java
SSLContext sslContext = SSLContexts.custom()
        .loadTrustMaterial((chain, authType) -> true)          // trust-all for dev
        .build();

CookieJar cookieJar = new CaffeineCacheCookieJar(
        10_000, Duration.ofHours(1), Duration.ofMinutes(30));

OkHttpClient client = new OkHttpClient.Builder()
        .sslSocketFactory(sslContext.getSocketFactory(),
                TrustManagerUtils.getTrustAllManager())
        .hostnameVerifier(new TrustAllHostnameVerifier())
        .cookieJar(cookieJar)
        .build();
```

**Expected result**: the client accepts any server certificate (dev only) and persists cookies in memory with a 1-hour write-expiry / 30-minute access-expiry policy.

<a id="7-configuration"></a>
## 7. Configuration

This is a pure Java library: all behavior is configured through constructors and builder methods — there are no configuration properties or `application.yml` entries.

| Entry | Configuration surface |
|---|---|
| `SSLContexts.custom()` | Fluent `SSLContextBuilder`: protocol, `SecureRandom`, `Provider`, keystore/truststore, key/trust strategies |
| `SSLContexts.createSSLContext(...)` | Static factory overloads (protocol + managers, keystore + `TrustStrategy`, ...) |
| `CaffeineCacheCookieJar` | `maximumSize`, `expireAfterWrite`, `expireAfterAccess` |
| `RequestRetryIntercepter` | `retryMaxAttempts`, `retryInterval` |
| `RequestHeaderInterceptor` | Custom `RequestHeaderProvider` implementation |
| `GzipRequestInterceptor` | `enabled` flag with `enable()` / `disable()` |

<a id="8-core-usage"></a>
## 8. Core Usage

### 8.1 Global request headers

```java
RequestHeaderInterceptor headerInterceptor = new RequestHeaderInterceptor(
        () -> List.of(new RequestHeaderInterceptor.HeaderEntry("X-App", "my-app")));

OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .build();
```

### 8.2 Retry with backoff interval

```java
RequestRetryIntercepter retry = new RequestRetryIntercepter(3, 1_000L); // 3 attempts, 1 s apart

OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(retry)
        .build();
```

### 8.3 Durable cookies

```java
// PersistenceCookieJar: override saveFromResponse / loadForRequest to store cookies
// in a database or file; loadForRequest returns previously saved cookies.
CookieJar jar = new PersistenceCookieJar() {
    @Override public void saveFromResponse(HttpUrl url, List<Cookie> cookies) { /* persist */ }
    @Override public List<Cookie> loadForRequest(HttpUrl url) { /* restore */ return List.of(); }
};
```

<a id="9-testing--build"></a>
## 9. Testing & Build

```bash
mvn clean verify
```

- The parent POM enforces Maven and JDK 8 baselines via `maven-enforcer-plugin`.
- Surefire is configured to run `**/*Tests.java` classes and to exclude `**/TestBean.java` helpers.
- JaCoCo runs `prepare-agent`, `report` and `check` on the `verify` phase with a **90% line-coverage** rule (`haltOnFailure=false`).
- Release packaging (`mvn -Prelease deploy`) attaches sources and javadoc jars, GPG-signs artifacts and is wired for Sonatype Central Publishing; plain `mvn deploy` routes SNAPSHOT/release artifacts to the Aliyun Maven repository per `distributionManagement`.
- `scripts/render-branch-pom.py` regenerates the branch-specific `pom.xml` (JDK / dependency stack per version line).

<a id="10-versioning--branches"></a>
## 10. Versioning & Branches

| Branch | Version pattern | JDK | Maintenance policy |
|---|---|---|---|
| `feature/1.0.x` (this branch) | `1.0.x.*` | 8 | Compatibility fixes and JDK-8-safe dependency upgrades only |
| `feature/2.0.x` | `2.0.x.*` | 17 | JDK 17 line |
| `feature/3.0.x` | `3.0.x.*` | 21 | JDK 21 line |

Each line is maintained per branch; dependency stacks and JDK baselines differ per line and are rendered into the branch POM by `scripts/render-branch-pom.py`.

<a id="11-contributing--license"></a>
## 11. Contributing & License

Contributions are welcome. Run `mvn clean verify` before opening a pull request and describe compatibility, testing and migration impact. This project is licensed under the [Apache License 2.0](LICENSE).
