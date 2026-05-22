FROM eclipse-temurin:17-jre

WORKDIR /app

# App jar
COPY target/*.jar /app/app.jar

# Optional external config and uploads can be mounted at runtime
# - /app/application-secrets.yml
# - /app/knowledge-base/uploads

EXPOSE 8090

ENV SERVER_PORT=8090

ENTRYPOINT ["java","-jar","/app/app.jar"]

