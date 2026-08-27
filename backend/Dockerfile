# §9.2 (Docker Compose imposé) et §15.2 ("construction des images Docker") ; §15.3
# ("endpoint de santé pour le back-end") - wget est installé dans l'image finale pour que
# le healthcheck de docker-compose.yml (wget -qO- http://localhost:8080/actuator/health)
# fonctionne, exactement comme ai-service/Dockerfile le fait déjà pour son propre point de
# santé.

# --- build ---------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21-jammy AS build
WORKDIR /app

# Couche de dépendances isolée du code source : ne se réinvalide que si pom.xml change,
# pas à chaque modification de src/.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Les tests (unitaires ET, via maven-failsafe-plugin, d'intégration - Testcontainers) ont
# leur propre étape dans le pipeline CI (.github/workflows/ci.yml) ; les rejouer ici
# alourdirait l'image sans rien vérifier de plus, et Testcontainers n'a de toute façon pas
# accès au démon Docker de l'hôte pendant un `docker build`.
RUN mvn -B package -DskipTests

# --- runtime ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home smartflow

COPY --from=build /app/target/backend-*.jar app.jar

USER smartflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
