# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -DskipTests package && \
    java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
