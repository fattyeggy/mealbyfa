# 第三方依赖声明 (THIRD_PARTY_NOTICES)

> 由 `scripts/gen-third-party.py` 生成。仅列**直接依赖**；
> 交付前建议在 CI 中运行完整扫描（Maven: license-maven-plugin；前端: license-checker）。

本项目自身代码的授权见 `LICENSE.md`。

## 后端（Maven，runtime 直接依赖）

| 依赖 | 版本 | 许可证 |
|---|---|---|
| `ch.qos.logback:logback-classic` | 1.5.11 | pom 未声明，需人工核对 |
| `ch.qos.logback:logback-core` | 1.5.11 | pom 未声明，需人工核对 |
| `com.baomidou:mybatis-plus` | 3.5.9 | The Apache License, Version 2.0 |
| `com.baomidou:mybatis-plus-annotation` | 3.5.9 | The Apache License, Version 2.0 |
| `com.baomidou:mybatis-plus-core` | 3.5.9 | The Apache License, Version 2.0 |
| `com.baomidou:mybatis-plus-extension` | 3.5.9 | The Apache License, Version 2.0 |
| `com.baomidou:mybatis-plus-jsqlparser` | 3.5.9 | The Apache License, Version 2.0 |
| `com.baomidou:mybatis-plus-jsqlparser-common` | 3.5.9 | The Apache License, Version 2.0 |
| `com.baomidou:mybatis-plus-spring` | 3.5.9 | The Apache License, Version 2.0 |
| `com.baomidou:mybatis-plus-spring-boot-autoconfigure` | 3.5.9 | The Apache License, Version 2.0 |
| `com.baomidou:mybatis-plus-spring-boot3-starter` | 3.5.9 | The Apache License, Version 2.0 |
| `com.fasterxml:classmate` | 1.7.0 | Apache License, Version 2.0 |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.17.2 | The Apache Software License, Version 2.0 |
| `com.fasterxml.jackson.core:jackson-core` | 2.17.2 | The Apache Software License, Version 2.0 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.17.2 | The Apache Software License, Version 2.0 |
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8` | 2.17.2 | pom 未声明，需人工核对 |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.17.2 | pom 未声明，需人工核对 |
| `com.fasterxml.jackson.module:jackson-module-parameter-names` | 2.17.2 | pom 未声明，需人工核对 |
| `com.github.jsqlparser:jsqlparser` | 5.0 | GNU Library or Lesser General Public License (LGPL) V2.1 / The Apache Software License, Version 2.0 |
| `com.h2database:h2` | 2.2.224 | EPL 1.0 / MPL 2.0 |
| `com.mysql:mysql-connector-j` | 8.3.0 | The GNU General Public License, v2 with Universal FOSS Exception, v1.0 |
| `com.zaxxer:HikariCP` | 5.1.0 | The Apache Software License, Version 2.0 |
| `io.micrometer:micrometer-commons` | 1.13.6 | The Apache Software License, Version 2.0 |
| `io.micrometer:micrometer-observation` | 1.13.6 | The Apache Software License, Version 2.0 |
| `jakarta.annotation:jakarta.annotation-api` | 2.1.1 | EPL 2.0 / GPL2 w/ CPE |
| `jakarta.validation:jakarta.validation-api` | 3.0.2 | Apache License 2.0 |
| `org.apache.logging.log4j:log4j-api` | 2.23.1 | Apache-2.0 |
| `org.apache.logging.log4j:log4j-to-slf4j` | 2.23.1 | Apache-2.0 |
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.1.31 | Apache License, Version 2.0 |
| `org.apache.tomcat.embed:tomcat-embed-el` | 10.1.31 | Apache License, Version 2.0 |
| `org.apache.tomcat.embed:tomcat-embed-websocket` | 10.1.31 | Apache License, Version 2.0 |
| `org.hibernate.validator:hibernate-validator` | 8.0.1.Final | pom 未声明，需人工核对 |
| `org.jboss.logging:jboss-logging` | 3.5.3.Final | Apache License 2.0 |
| `org.mybatis:mybatis` | 3.5.16 | pom 未声明，需人工核对 |
| `org.mybatis:mybatis-spring` | 3.0.4 | pom 未声明，需人工核对 |
| `org.projectlombok:lombok` | 1.18.34 | The MIT License |
| `org.slf4j:jul-to-slf4j` | 2.0.16 | pom 未声明，需人工核对 |
| `org.slf4j:slf4j-api` | 2.0.16 | pom 未声明，需人工核对 |
| `org.springframework:spring-aop` | 6.1.14 | Apache License, Version 2.0 |
| `org.springframework:spring-beans` | 6.1.14 | Apache License, Version 2.0 |
| `org.springframework:spring-context` | 6.1.14 | Apache License, Version 2.0 |
| `org.springframework:spring-core` | 6.1.14 | Apache License, Version 2.0 |
| `org.springframework:spring-expression` | 6.1.14 | Apache License, Version 2.0 |
| `org.springframework:spring-jcl` | 6.1.14 | Apache License, Version 2.0 |
| `org.springframework:spring-jdbc` | 6.1.14 | Apache License, Version 2.0 |
| `org.springframework:spring-tx` | 6.1.14 | Apache License, Version 2.0 |
| `org.springframework:spring-web` | 6.1.14 | Apache License, Version 2.0 |
| `org.springframework:spring-webmvc` | 6.1.14 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot` | 3.3.5 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-autoconfigure` | 3.3.5 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter` | 3.3.5 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter-jdbc` | 3.3.5 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter-json` | 3.3.5 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter-logging` | 3.3.5 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter-tomcat` | 3.3.5 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter-validation` | 3.3.5 | Apache License, Version 2.0 |
| `org.springframework.boot:spring-boot-starter-web` | 3.3.5 | Apache License, Version 2.0 |
| `org.springframework.security:spring-security-crypto` | 6.3.4 | Apache License, Version 2.0 |
| `org.yaml:snakeyaml` | 2.2 | Apache License, Version 2.0 |

## 运营后台（npm）

| 依赖 | 声明版本 | 许可证 |
|---|---|---|
| `@element-plus/icons-vue` | ^2.3.2 | MIT |
| `element-plus` | ^2.14.8 | MIT |
| `vue` | ^3.5.45 | MIT |
| `vue-router` | ^4.6.5 | MIT |

## 小程序（npm）

| 依赖 | 声明版本 | 许可证 |
|---|---|---|
| `@dcloudio/uni-app` | ^3.0.0-4080720251210001 | Apache-2.0 |
| `@dcloudio/uni-h5` | ^3.0.0-4080720251210001 | Apache-2.0 |
| `@dcloudio/uni-mp-vue` | ^3.0.0-4080720251210001 | Apache-2.0 |
| `@dcloudio/uni-mp-weixin` | ^3.0.0-4080720251210001 | Apache-2.0 |
| `vue` | ^3.4.21 | MIT |

## 说明

1. Spring Boot、MyBatis-Plus、H2、MySQL Connector/J、Lombok 等为 Apache-2.0 或兼容许可；
2. Vue、Vite、Element Plus 为 MIT；uni-app 系列遵循 DCloud 许可，商用前请核对官方条款；
3. 本项目未内嵌或修改上述依赖源码，均以二进制/包依赖方式引用；
4. 若后续引入 GPL/AGPL 依赖，需在交付评审中单独标注。