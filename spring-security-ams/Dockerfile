FROM openjdk:17-jdk-slim AS runtime

WORKDIR /spring-ams

COPY src/main/resources/target /dcl-data

COPY target/*.jar /app.jar
ENTRYPOINT ["java",  "-jar", "/app.jar"]
EXPOSE 8080