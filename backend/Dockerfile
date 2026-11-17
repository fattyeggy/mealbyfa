# 微信云托管：仓库根目录构建
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY settings.xml .
COPY backend/pom.xml .
COPY backend/src ./src
RUN mvn -B -ntp -s settings.xml clean package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /build/target/sancan-meal-service.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xmx768m","-Duser.timezone=Asia/Shanghai","-jar","/app/app.jar","--spring.profiles.active=mysql,prod","--server.port=8080"]
