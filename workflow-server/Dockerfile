FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SERVER_PORT=8080
ENV FILE_STORAGE_PATH=/app/uploads
COPY --from=build /app/target/workflow-server-1.0.0.jar /app/workflow-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/workflow-server.jar"]
