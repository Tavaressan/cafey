# syntax=docker/dockerfile:1
#
# Multi-stage build manual (não bootBuildImage/Buildpacks): a issue #149 pedia para avaliar
# bootBuildImage antes de partir para um Dockerfile, mas bootBuildImage não se integra ao fluxo
# `docker compose up --build` (Buildpacks não é um backend de build suportado nativamente pelo
# Compose) — exigiria um passo manual extra no host (`./gradlew bootBuildImage`) antes de subir a
# stack, o que viola o critério de aceite "sem passo manual no host". Um Dockerfile multi-stage
# permite que o Compose construa a imagem sozinho na primeira execução.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copia primeiro o wrapper e os arquivos de build para aproveitar o cache de camadas do Docker
# quando só o código-fonte mudar.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew --no-daemon help >/dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
