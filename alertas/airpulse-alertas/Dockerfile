FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Apenas JRE para execução contínua (scheduler de 1 min)
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/*-jar-with-dependencies.jar app.jar

ENV aws_region="us-east-1" \
    aws_s3_alertas_prefix="03-gold/" \
    JIRA_ALERT_ISSUE_TYPE="Task"

ENTRYPOINT ["java", "-jar", "app.jar"]
