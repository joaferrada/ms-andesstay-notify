# syntax=docker/dockerfile:1

# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Primero solo lo necesario para resolver dependencias (capa cacheada mientras no cambie el pom)
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar app.jar

# ---------- Runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system andesstay && useradd --system --gid andesstay --no-create-home andesstay

COPY --from=build --chown=andesstay:andesstay /app/app.jar app.jar

USER andesstay
EXPOSE 8083

# Se puede sobrescribir desde compose (ej. agregar -Xss o flags de GC)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
