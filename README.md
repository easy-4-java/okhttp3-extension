# okhttp3-extension

纯 Java OkHttp 扩展层，承载 SSL、CookieJar、Interceptor、Response/工具类，不包含 Spring Boot 自动配置。

## Maven

```xml
<dependency>
  <groupId>io.github.hiwepy</groupId>
  <artifactId>okhttp3-extension</artifactId>
  <version>2.0.x.20260630-SNAPSHOT</version>
</dependency>
```

## 版本线

| 分支 | 版本前缀 | JDK | 用途 |
|------|----------|-----|------|
| `feature/1.0.x` | `1.0.x.*` | 8 | 供 Boot 2.x Starter 使用 |
| `feature/2.0.x` | `2.0.x.*` | 17 | 供 Boot 3.x Starter 使用 |
| `feature/3.0.x` | `3.0.x.*` | 21 | 供 Boot 4.x Starter 使用 |

## 分层关系

- 本模块：纯 Java 扩展能力
- [okhttp3-metrics-prometheus](../okhttp3-metrics-prometheus)：纯 Java metrics sidecar
- [okhttp3-spring-boot-starter](../okhttp3-spring-boot-starter)：Spring Boot 自动装配层

## 生成分支 POM

```bash
python3 scripts/render-branch-pom.py feature/2.0.x
```

## License

Apache License 2.0
