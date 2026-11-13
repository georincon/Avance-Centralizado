# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies -q > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
 && cp build/libs/onboarding-backend-*-SNAPSHOT.jar /app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 app && mkdir -p /data/uploads && chown -R app /data
USER app
COPY --from=build /app.jar /app.jar
ENV SERVER_PORT=8095 STORAGE_LOCAL_PATH=/data/uploads
EXPOSE 8095
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app.jar"]
