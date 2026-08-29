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
    && useradd --system --no-create-home smartflow \
    # RG-09/§13 - le volume nommé `attachments` de docker-compose.yml se monte sur ce
    # chemin (smartflow.attachments.storage-dir) ; Docker crée un volume nommé fraîchement
    # créé en copiant ce que l'image contient déjà à ce chemin, propriétaire compris. Sans
    # ce répertoire pré-créé et cédé à `smartflow` ici, Docker le crée appartenant à root
    # (aucune image ne le possède avant), illisible en écriture par le process non-root qui
    # tourne réellement (USER smartflow ci-dessous) - AttachmentStorage échouerait alors sur
    # chaque dépôt avec un AccessDeniedException, constaté en conditions réelles (docker
    # compose up), pas seulement en Testcontainers où smartflow.attachments.storage-dir
    # pointe vers un répertoire temporaire déjà possédé par l'utilisateur qui lance mvn.
    && mkdir -p /var/smartflow/attachments \
    && chown -R smartflow:smartflow /var/smartflow

COPY --from=build /app/target/backend-*.jar app.jar

USER smartflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
