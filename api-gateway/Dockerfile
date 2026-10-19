FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# Se elimina pebble (binario Go de la imagen base, sin uso aqui): trae CVEs HIGH de stdlib
RUN useradd --system --no-create-home appuser && rm -f /usr/bin/pebble
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]