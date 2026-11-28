FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/dependency

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /app/target ./target
COPY web ./web
EXPOSE 8080
CMD ["java", "-cp", "target/classes:target/dependency/*", "src.WebServer"]
