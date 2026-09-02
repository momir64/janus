FROM node:22-alpine AS frontend
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS backend
WORKDIR /src
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY backend/src ./src
RUN ./gradlew --no-daemon buildFatJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /src/build/libs/kredenac-all.jar kredenac.jar
COPY --from=frontend /src/dist ./static
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "kredenac.jar"]
