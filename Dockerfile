FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21
WORKDIR /app
RUN groupadd -r appgroup && useradd -r -g appgroup -u 1000 appuser
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
COPY --from=build /workspace/target/*.jar /app/app.jar
USER appuser
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
