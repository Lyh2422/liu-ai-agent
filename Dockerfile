# Build the application in a disposable Maven image.
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /build

# 只复制必要的源代码和配置文件
COPY pom.xml .
COPY src ./src

# 使用 Maven 执行打包
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

# Keep Maven and source files out of the runtime image.
FROM amazoncorretto:21-alpine
WORKDIR /app

RUN addgroup -S appgroup \
    && adduser -S appuser -G appgroup \
    && mkdir -p /app/tmp \
    && chown -R appuser:appgroup /app

COPY --from=build /build/target/liu-ai-agent-0.0.1-SNAPSHOT.jar /app/app.jar

USER appuser
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8123

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
