# 阶段 1：使用 Maven 镜像进行编译打包
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# 忽略测试进行打包
RUN mvn clean package -DskipTests

# 阶段 2：使用轻量级 JRE 镜像运行
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 复制阶段 1 打包出的 jar
COPY --from=build /app/target/reqflow-0.0.1-SNAPSHOT.jar app.jar

# 暴露 8080 端口
EXPOSE 8080

# 启动命令，强制激活 prod 生产环境配置
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]