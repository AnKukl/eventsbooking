# syntax=docker/dockerfile:1

# ---- Stage 1: build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependencies first: this layer is rebuilt only when the pom changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests need Docker (Testcontainers), so they run in CI / locally via `./mvnw verify`, not inside the image build
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/*.jar extract --layers --destination target/extracted

# ---- Stage 2: runtime (JRE only, no build tools, no sources) ----
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

# Layers from least to most frequently changed
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

# Uploaded event photos: a directory the unprivileged user may write to; compose mounts a volume here
RUN mkdir -p /app/data/uploads && chown -R app:app /app/data
ENV STORAGE_DIR=/app/data/uploads

USER app
EXPOSE 8080

# No secrets here: JWT_SECRET, DB_PASSWORD, ... are passed at run time as environment variables
ENTRYPOINT ["java", "-jar", "eventsbooking-0.0.1-SNAPSHOT.jar"]
