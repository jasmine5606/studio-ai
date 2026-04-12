<<<<<<< HEAD
# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN mvn dependency:go-offline -B -q

COPY src src
RUN mvn package -DskipTests -B -q

# Stage 2: Runtime
=======
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
>>>>>>> 8745bcdd3d147b7a95463f51089e7e1ff9bbb0dc
FROM eclipse-temurin:17-jre

WORKDIR /app

<<<<<<< HEAD
COPY --from=builder /build/target/*.jar /app/app.jar
=======
COPY --from=build /build/target/*.jar /app/app.jar

# Optional external config and uploads can be mounted at runtime
# - /app/application-secrets.yml
# - /app/knowledge-base/uploads
>>>>>>> 8745bcdd3d147b7a95463f51089e7e1ff9bbb0dc

EXPOSE 8090

ENV SERVER_PORT=8090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
<<<<<<< HEAD
=======

>>>>>>> 8745bcdd3d147b7a95463f51089e7e1ff9bbb0dc
