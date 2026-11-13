# Spartans Wallet Provider - build local (requiere Java 25, por eso se compila dentro de Docker)
FROM gradle:jdk25 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew \
 && ./gradlew --no-daemon -I docker/runtime.init.gradle :wallet-provider-service:copyRuntime

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/wallet-provider-service/build/dist /app/lib
COPY openapi/openapi.json /openapi/openapi.json
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "/app/lib/*", "eu.europa.ec.eudi.walletprovider.MainKt"]
