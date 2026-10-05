FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build /build/target/dcompany.jar ./dcompany.jar

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "/app/dcompany.jar"]
CMD ["--help"]
