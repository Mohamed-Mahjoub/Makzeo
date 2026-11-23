# ---------------------------------------------------------------------------
# Dockerfile unique pour tous les microservices (build multi-étapes, utilisé par docker compose).
#   docker build --build-arg SERVICE=product-service -t makzeo/product-service .
# En intégration continue, Dockerfile.runtime réutilise le JAR déjà testé (build once, deploy many).
# ---------------------------------------------------------------------------

# --- Étape 1 : compilation Maven ------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace

# Copie des POM d'abord : la résolution des dépendances est mise en cache
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY auth-service/pom.xml auth-service/
COPY product-service/pom.xml product-service/
COPY supplier-service/pom.xml supplier-service/
COPY stock-service/pom.xml stock-service/
COPY reporting-service/pom.xml reporting-service/
RUN mvn -B -q -pl ${SERVICE} -am dependency:go-offline -DskipTests || true

COPY common-lib common-lib
COPY ${SERVICE} ${SERVICE}
RUN mvn -B -pl ${SERVICE} -am package -DskipTests \
 && java -Djarmode=tools -jar ${SERVICE}/target/${SERVICE}.jar extract --layers --launcher --destination /workspace/extracted

# --- Étape 2 : image d'exécution ------------------------------------------
FROM eclipse-temurin:21-jre
ARG SERVICE
LABEL org.opencontainers.image.title="${SERVICE}" \
      org.opencontainers.image.description="Makzeo - ${SERVICE}" \
      org.opencontainers.image.vendor="Makzeo"

RUN groupadd --system --gid 1001 app && useradd --system --uid 1001 --gid app --home /app app
WORKDIR /app

# Couches Spring Boot : les dépendances changent rarement -> meilleur cache Docker
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# UID numérique : permet à Kubernetes de vérifier runAsNonRoot
USER 1001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
