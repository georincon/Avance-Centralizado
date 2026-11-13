FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/issuer.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/issuer.jar"]
