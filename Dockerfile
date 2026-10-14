FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY *.java ./
COPY index.html application.properties ./
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/campuslock-1.0.0.jar app.jar
ENV PORT=8080
ENV CAMPUSLOCK_DB_PATH=/var/data/campuslock.db
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
