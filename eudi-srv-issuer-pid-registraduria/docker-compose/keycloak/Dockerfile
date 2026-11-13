FROM maven:3.9-eclipse-temurin-17-alpine AS downloader
ARG VERSION
RUN if [[ "${VERSION}" == *"-SNAPSHOT" ]]; then \
      REPOSITORY="https://central.sonatype.com/repository/maven-snapshots/"; \
    else \
      REPOSITORY="https://repo.maven.apache.org/maven2/"; \
    fi && \
    mvn dependency:get \
      -DremoteRepositories=${REPOSITORY} \
      -Dartifact="eu.europa.ec.eudi:abca-keycloak-ext:${VERSION}" \
      -Dtransitive=false
RUN mvn dependency:copy \
      -Dartifact="eu.europa.ec.eudi:abca-keycloak-ext:${VERSION}" \
      -DoutputDirectory=/tmp

# Extensión propia: login automático con issuer_state (OpenID4VCI), ver ext/issuer-state-authenticator
FROM maven:3.9-eclipse-temurin-21 AS ext-builder
WORKDIR /build
COPY ext/issuer-state-authenticator/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY ext/issuer-state-authenticator/src ./src
RUN mvn -q -B package -DskipTests

FROM quay.io/keycloak/keycloak:26.6.2-2
COPY --from=ext-builder /build/target/issuer-state-authenticator.jar /opt/keycloak/providers/issuer-state-authenticator.jar
COPY --from=downloader /tmp/abca-keycloak-ext-*.jar /opt/keycloak/providers/abca-keycloak-ext.jar
USER root
RUN chmod 644 /opt/keycloak/providers/*.jar
USER keycloak
