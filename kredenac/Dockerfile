FROM node:22-alpine AS frontend
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS backend
WORKDIR /src
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
COPY backend/src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon buildFatJar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S kredenac && adduser -S -G kredenac kredenac
WORKDIR /app
COPY --from=backend /src/build/libs/kredenac-all.jar kredenac.jar
COPY --from=frontend /src/dist ./static
USER kredenac
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "kredenac.jar"]
