# Multi-stage: build the fat JAR, then run it on a JRE.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/sre-agent.jar /app/app.jar
EXPOSE 8085
# Runs as the HTTP service by default. For one-shot CLI mode, override:
#   docker run ... sre-agent --analyze --window=10 --target=sample-app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
