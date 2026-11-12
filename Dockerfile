# One image with the whole app: Spring Boot serves the API and the built Angular app from the same origin.
# Build:  docker build -t acme-salary .
# Run:    docker run -p 8080:8080 -e APP_ADMIN_PASSWORD=... acme-salary   (see README.md for the settings)

# ---- 1. Build the Angular app ------------------------------------------------------------------
FROM node:24.22.3-alpine AS frontend
WORKDIR /build/frontend
# Dependencies first, so they are cached until package*.json changes.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build

# ---- 2. Build the Spring Boot jar, with the Angular files inside it ------------------------------
FROM eclipse-temurin:17-jdk AS backend
WORKDIR /build/backend
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY backend/src src
COPY --from=frontend /build/frontend/dist/frontend/browser/ src/main/resources/static/
# Tests run in development and before each commit; the image build only packages.
RUN ./mvnw -q -B -DskipTests package

# ---- 3. Run it ---------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
RUN useradd --system --create-home --uid 10001 app && mkdir -p /app/data && chown app /app/data

# Sized for a 512 MB instance with a fraction of a CPU: the heap may use 70% of the container's memory, the
# serial collector and smaller thread stacks keep the rest of the JVM small, and the quicker C1 compiler
# alone shortens start-up. Set before the training run below, because its archive must match these flags.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"

# Faster start-up with class data sharing: unpack the jar, start the app once here so the JVM records the
# classes it loads (it stops as soon as the app has started, before the seed runs), and reuse that record
# on every real start.
WORKDIR /app
COPY --from=backend /build/backend/target/salary-management-0.0.1-SNAPSHOT.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination application && rm app.jar
WORKDIR /app/application
RUN APP_DB_PATH=/tmp/cds-training.db APP_SEED_ENABLED=false \
    java -XX:ArchiveClassesAtExit=application.jsa -Dspring.context.exit=onRefresh -jar app.jar \
    && rm -f /tmp/cds-training.db*

USER app
# The SQLite file. On a free host this folder is wiped on restart, and the seed refills it with demo data.
ENV APP_DB_PATH=/app/data/acme.db
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=application.jsa", "-jar", "app.jar"]
