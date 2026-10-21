# Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

# Run (non-root, OpenShift-friendly: any UID in group 0 can read the jar)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/dairy-farm-backend-*.jar app.jar
RUN chgrp 0 app.jar && chmod g=u app.jar
USER 1001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
