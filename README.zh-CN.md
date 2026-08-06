# okhttp3-extension

[English](./README.md) | [简体中文](./README.zh-CN.md)

纯 Java OkHttp 3/4 扩展层：SSL 工具、CookieJar、拦截器与响应工具类

> **当前分支**：`feature/3.0.x`
> **版本**：`3.0.x.x.20260630-SNAPSHOT`
> **JDK 基线**：8
> **项目状态**：维护中（1.0.x 线）。尚未发布 Maven Central；制品通过 Aliyun Maven 仓库与 GitHub Releases 分发。

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 能力与状态](#2-features--status)
- [3. 运行要求与兼容性](#3-requirements--compatibility)
- [4. 架构与模块](#4-architecture--modules)
- [5. 引入依赖](#5-installation)
- [6. 快速开始](#6-quick-start)
- [7. 配置](#7-configuration)
- [8. 核心用法](#8-core-usage)
- [9. 测试与构建](#9-testing--build)
- [10. 版本线与分支](#10-versioning--branches)
- [11. 贡献与许可证](#11-contributing--license)

## 1. 项目概述

### 1.1 是什么

**okhttp3-extension** 是面向 OkHttp 的纯 Java 扩展层，在官方 OkHttp API 之上提供 SSL/TLS 构建块、Cookie 存储策略、请求 / 响应拦截器与响应工具类。本模块**不包含 Spring Boot 自动配置**。

### 1.2 不是什么

- **不是 Spring Boot Starter**。自动装配位于独立的 `okhttp3-spring-boot-starter` 仓库；本模块保持框架无关。
- **不是 OkHttp 的分支**。基于官方 `okhttp` 构件（本线为 4.12.0）扩展。
- **不是指标模块**。Prometheus / Micrometer 埋点属于兄弟模块：[okhttp3-metrics-prometheus](https://github.com/easy-4-java/okhttp3-metrics-prometheus)。

### 1.3 典型使用场景

| 场景 | 推荐入口 | 结果 |
|---|---|---|
| 开发 / 受控环境的信任全部或自定义信任 TLS 客户端 | `SSLContexts.custom()`、`TrustManagerUtils`、`SSLContextBuilder` | 可直接用于 `OkHttpClient` 的 `SSLContext` |
| 客户端证书（双向 TLS） | `SSLContextBuilder.loadKeyMaterial(...)` | 支持 mTLS 的 `SSLContext` |
| 进程重启后仍保留 Cookie | `PersistenceCookieJar` | Cookie 状态跨重启持久化 |
| 有过期时间、大小受限的内存 Cookie | `CaffeineCacheCookieJar` | TTL / 访问过期 Cookie 缓存 |
| 同时使用多个 Cookie 来源 | `NestedCookieJar` | 保存扇出 / 读取合并 |
| 全局请求头注入 | `RequestHeaderInterceptor` | 每个请求自动携带指定 Header |
| 失败重试 | `RequestRetryIntercepter` | 可配置重试次数与间隔 |
| GZIP 请求体 | `GzipRequestInterceptor` | 上传内容压缩 |

<a id="2-features--status"></a>
## 2. 能力与状态

| 能力 | 状态 | 说明 |
|---|:---:|---|
| SSL 上下文构建 | 可用 | `SSLContextBuilder`（流式）、`SSLContexts`（工厂）、自定义 `KeyManager` / `TrustManager` 策略 |
| 信任全部主机名校验器 | 可用 | `TrustAllHostnameVerifier` |
| 持久化 CookieJar | 可用 | `PersistenceCookieJar` |
| Caffeine 缓存 CookieJar | 可用 | `CaffeineCacheCookieJar(maximumSize, expireAfterWrite, expireAfterAccess)` |
| 嵌套 CookieJar | 可用 | `NestedCookieJar(List<CookieJar>)` |
| 请求头拦截器 | 可用 | `RequestHeaderInterceptor` + `RequestHeaderProvider` |
| 重试拦截器 | 可用 | `RequestRetryIntercepter(retryMaxAttempts, retryInterval)` |
| GZIP 请求拦截器 | 可用 | `GzipRequestInterceptor(enabled)` |
| 响应工具类 | 可用 | `Okhttp3Response.isSuccess()` |
| Spring Boot 自动配置 | 不包含 | 见独立的 `okhttp3-spring-boot-starter` |

<a id="3-requirements--compatibility"></a>
## 3. 运行要求与兼容性

| 组件 | 版本 | 说明 |
|---|---:|---|
| JDK | 21+ | 由 `maven-enforcer-plugin` 强制校验 |
| Maven | 3.0+ | Enforcer 下限 |
| OkHttp | 4.12.0 | 通过 `okhttp-bom` 引入 |
| Jackson annotations | 2.17.2 | 通过 `jackson-bom` 引入 |
| Caffeine | 2.9.3 | Cookie 缓存 |
| SLF4J | 2.0.18 | 日志门面 |

版本线矩阵：

| 版本线 | 分支 | JDK | 版本模式 | 用途 |
|---|---|---:|---|---|
| 1.0.x | `feature/3.0.x`（当前分支） | 8 | `1.0.x.*` | 供 Boot 2.x Starter 与存量项目使用 |
| 2.0.x | `feature/2.0.x` | 17 | `2.0.x.*` | 供 Boot 3.x Starter 使用 |
| 3.0.x | `feature/3.0.x` | 21 | `3.0.x.*` | 供 Boot 4.x Starter / 新项目使用 |

<a id="4-architecture--modules"></a>
## 4. 架构与模块

```text
[ 业务应用 ]
        |
        | 使用 okhttp3-extension 配置 OkHttpClient
        v
+------------------------------------------+
| SSL        SSLContexts / SSLContextBuilder|
|            TrustManagerUtils / KeyManager |
| Cookie     PersistenceCookieJar /         |
|            CaffeineCacheCookieJar /       |
|            NestedCookieJar                |
| 拦截器      Gzip / RequestHeader /        |
|            RequestRetry / Network         |
| 响应        Okhttp3Response 工具类        |
+------------------------------------------+
        |
        v
[ OkHttp (4.12.0) ] -> [ HTTP(S) 端点 ]
```

单模块库（打包类型 `jar`）。包结构：

| 包 | 职责 |
|---|---|
| `okhttp3.extension` | 响应工具类（`Okhttp3Response`） |
| `okhttp3.extension.ssl` | SSL/TLS 构建块（`SSLContextBuilder`、`SSLContexts`、信任 / 密钥管理器、主机名校验器） |
| `okhttp3.extension.cookie` | 内存 CookieJar（`CaffeineCacheCookieJar`、`NestedCookieJar`） |
| `okhttp3.extension.cache` | 持久化 CookieJar（`PersistenceCookieJar`） |
| `okhttp3.extension.interceptor` | 拦截器（`GzipRequestInterceptor`、`RequestHeaderInterceptor`、`RequestRetryIntercepter`、`RequestInterceptor` / `NetworkInterceptor` / `ProxyAuthenticator` 契约） |

<a id="5-installation"></a>
## 5. 引入依赖

Maven：

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>okhttp3-extension</artifactId>
    <version>3.0.x.x.20260630-SNAPSHOT</version>
</dependency>
```

Gradle：

```groovy
implementation 'io.github.easy4j:okhttp3-extension:3.0.x.x.20260630-SNAPSHOT'
```

快照版本需要启用对应快照仓库（`pom.xml` 中 `distributionManagement` 指向 Aliyun Maven 仓库）。

<a id="6-quick-start"></a>
## 6. 快速开始

构建一个带自定义信任 SSL 上下文与过期策略 CookieJar 的客户端：

```java
SSLContext sslContext = SSLContexts.custom()
        .loadTrustMaterial((chain, authType) -> true)          // 开发环境信任全部证书
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

**预期结果**：客户端接受任意服务端证书（仅限开发环境），并以「写入 1 小时过期、访问 30 分钟过期」的策略在内存中管理 Cookie。

<a id="7-configuration"></a>
## 7. 配置

本库为纯 Java 库：所有行为通过构造器与 Builder 方法配置，**无配置属性、无 `application.yml` 条目**。

| 入口 | 配置面 |
|---|---|
| `SSLContexts.custom()` | 流式 `SSLContextBuilder`：协议、`SecureRandom`、`Provider`、密钥库 / 信任库、密钥 / 信任策略 |
| `SSLContexts.createSSLContext(...)` | 静态工厂重载（协议 + 管理器、密钥库 + `TrustStrategy` 等） |
| `CaffeineCacheCookieJar` | `maximumSize`、`expireAfterWrite`、`expireAfterAccess` |
| `RequestRetryIntercepter` | `retryMaxAttempts`、`retryInterval` |
| `RequestHeaderInterceptor` | 自定义 `RequestHeaderProvider` 实现 |
| `GzipRequestInterceptor` | `enabled` 开关，支持 `enable()` / `disable()` |

<a id="8-core-usage"></a>
## 8. 核心用法

### 8.1 全局请求头

```java
RequestHeaderInterceptor headerInterceptor = new RequestHeaderInterceptor(
        () -> List.of(new RequestHeaderInterceptor.HeaderEntry("X-App", "my-app")));

OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .build();
```

### 8.2 带间隔的重试

```java
RequestRetryIntercepter retry = new RequestRetryIntercepter(3, 1_000L); // 3 次尝试，间隔 1 秒

OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(retry)
        .build();
```

### 8.3 持久化 Cookie

```java
// PersistenceCookieJar：覆写 saveFromResponse / loadForRequest，将 Cookie 存入数据库或文件；
// loadForRequest 返回此前保存的 Cookie。
CookieJar jar = new PersistenceCookieJar() {
    @Override public void saveFromResponse(HttpUrl url, List<Cookie> cookies) { /* 持久化 */ }
    @Override public List<Cookie> loadForRequest(HttpUrl url) { /* 恢复 */ return List.of(); }
};
```

<a id="9-testing--build"></a>
## 9. 测试与构建

```bash
mvn clean verify
```

- 父 POM 通过 `maven-enforcer-plugin` 强制 Maven 与 JDK 8 基线。
- Surefire 配置为运行 `**/*Tests.java`，并排除 `**/TestBean.java` 辅助类。
- JaCoCo 在 `verify` 阶段执行 `prepare-agent`、`report` 与 `check`，行覆盖率规则为 **90%**（`haltOnFailure=false`）。
- 发布打包（`mvn -Prelease deploy`）附带 sources 与 javadoc 构件并执行 GPG 签名，对接 Sonatype Central Publishing；普通 `mvn deploy` 按版本后缀路由到 Aliyun Maven 仓库（见 `distributionManagement`）。
- `scripts/render-branch-pom.py` 按版本线重新生成分支专属 `pom.xml`（JDK 与依赖栈随线变化）。

<a id="10-versioning--branches"></a>
## 10. 版本线与分支

| 分支 | 版本模式 | JDK | 维护策略 |
|---|---|---|---|
| `feature/1.0.x`（当前分支） | `1.0.x.*` | 8 | 仅接受兼容性修复与 JDK 8 安全的依赖升级 |
| `feature/2.0.x` | `2.0.x.*` | 17 | JDK 17 线 |
| `feature/3.0.x` | `3.0.x.*` | 21 | JDK 21 线 |

各版本线按分支独立维护；依赖栈与 JDK 基线随线变化，由 `scripts/render-branch-pom.py` 渲染进分支 POM。

<a id="11-contributing--license"></a>
## 11. 贡献与许可证

欢迎贡献。提交 Pull Request 前请执行 `mvn clean verify`，并说明兼容性、测试与迁移影响。本项目采用 [Apache License 2.0](LICENSE) 许可证。
