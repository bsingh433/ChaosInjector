# Optional: run ChaosInjector itself as a container.
#
# Multi-stage build — compiles the React UI + Spring Boot backend into the single
# fat JAR, then runs it on a JRE. The primary supported deployment is still
# running the JAR directly on the host (see README); this image is a convenience.
#
# Build:
#   docker build -t chaosinjector:latest .
# Run (mount the Docker socket so it can reach the local daemon):
#   docker run --rm -p 8080:8080 -v /var/run/docker.sock:/var/run/docker.sock chaosinjector:latest
#
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Node for the frontend build
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && apt-get install -y nodejs

COPY backend/pom.xml backend/pom.xml
COPY backend/.mvn backend/.mvn
COPY backend backend
COPY frontend frontend

RUN cd backend && ./mvnw -B -Pprod clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/backend/target/chaosinjector.jar /app/chaosinjector.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/chaosinjector.jar"]
