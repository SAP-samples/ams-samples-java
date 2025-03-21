FROM --platform=linux/amd64 openjdk:17.0.2-jdk-slim AS runtime

COPY dcldeployer/dcl/ /dcl

COPY target/*.jar /app.jar
ENTRYPOINT ["java",  "-jar", "/app.jar"]
EXPOSE 8080