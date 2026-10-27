FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
# 内置参考音频：后端 resources/reference/reference.wav 会打进 jar 包的 classpath，这里额外拷一份到文件系统供 application-prod.yml 的路径使用
RUN mkdir -p /app/data
COPY --from=build /app/classes/reference/reference.wav /app/data/reference.wav 2>/dev/null || true
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=prod -jar app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
  CMD curl -f http://localhost:8080/ || exit 1