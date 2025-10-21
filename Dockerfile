# ==========================
# 1. Build Stage
# ==========================
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom.xml and download dependencies (for caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the JAR
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================
# 2. Runtime Stage
# ==========================
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# Copy built JAR from previous stage
COPY --from=builder /app/target/*.jar app.jar

# Set timezone (optional)
ENV TZ=Asia/Tokyo

# Expose port (Render uses this to detect your service)
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
