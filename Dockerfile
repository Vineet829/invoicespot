# The backend: Maven builds the jar, which is split into layers so a rebuild
# that only changes our code reuses the dependency layers. The jar runs on any
# CPU, so it is built on the build machine's own platform even when the image
# is for another one.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Tests run in CI, not here. The Maven cache outlives the build, so
# dependencies download once.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package \
    && cp target/invoice-spot-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# Not Alpine: PDFs need AWT and the fonts this image already has.
FROM eclipse-temurin:21-jre-noble
WORKDIR /app
# Its own user, which can write only to /app/data, where logos are kept.
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/data \
    && chown app:app /app/data
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER app
ENV UPLOAD_DIR=/app/data/uploads
# PDFBox keeps its font cache in the home directory, which is read-only here.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Dpdfbox.fontcache=/tmp"
EXPOSE 8080
# Health is on the management port once MANAGEMENT_PORT moves it there.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --start-interval=5s --retries=3 \
    CMD curl -fsS -o /dev/null "http://127.0.0.1:${MANAGEMENT_PORT:-${PORT:-8080}}/actuator/health" || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
