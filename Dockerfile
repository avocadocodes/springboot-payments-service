# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy POM first — lets Docker cache the dependency download layer separately
COPY pom.xml .
RUN mvn -q dependency:go-offline -B

COPY src ./src
RUN mvn -q -DskipTests package -B

# ---- runtime stage ----
FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app

# Non-root user for the process
RUN addgroup --system payments && adduser --system --ingroup payments payments
USER payments

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
