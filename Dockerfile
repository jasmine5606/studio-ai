# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN mvn dependency:go-offline -B -q

COPY src src
RUN mvn package -DskipTests -B -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /build/target/*.jar /app/app.jar

EXPOSE 8090

ENV SERVER_PORT=8090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
