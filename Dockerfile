# ---- Étape 1 : compilation avec Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- Étape 2 : image légère qui exécute seulement le .jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/repetipro.jar app.jar
# Render fournit la variable PORT ; l'appli l'utilise (server.port=${PORT:8080})
EXPOSE 8080
# -Xmx350m : on reste sous les 512 Mo de l'offre gratuite de Render
ENTRYPOINT ["java", "-Xmx350m", "-XX:+UseSerialGC", "-Duser.timezone=Africa/Porto-Novo", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
