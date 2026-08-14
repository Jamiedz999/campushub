# syntax=docker/dockerfile:1

FROM node:24-slim AS web-build
WORKDIR /web
# Cypress is a devDependency of web/, and its postinstall downloads a few hundred megabytes of browser
# that this image has no use for: the journeys run against the running container, never inside it.
ENV CYPRESS_INSTALL_BINARY=0
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS server-build
WORKDIR /server
COPY server/.mvn/ .mvn/
COPY server/mvnw server/pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY server/src src
COPY --from=web-build /web/dist src/main/resources/static
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --create-home --home-dir /app appuser
WORKDIR /app
COPY --from=server-build --chown=appuser:appuser /server/target/campushub-server.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
