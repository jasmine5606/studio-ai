# ---- Stage 1: Build with Maven ----
FROM eclipse-temurin:17-jdk AS build

WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /build/target/*.jar /app/app.jar

# Optional external config and uploads can be mounted at runtime
# - /app/application-secrets.yml
# - /app/knowledge-base/uploads

EXPOSE 8090

ENV SERVER_PORT=8090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

