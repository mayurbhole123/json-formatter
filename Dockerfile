# --- Stage 1: build the fat jar ---------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Copy the wrapper first so dependency resolution is cached independently of src/
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp clean package -DskipTests

# --- Stage 2: runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as an unprivileged user; the pod security context expects uid/gid 1000
RUN addgroup -g 1000 -S app && adduser -u 1000 -S app -G app
COPY --from=build --chown=app:app /build/target/json-formatter.jar app.jar
USER app

EXPOSE 8080

# Let the JVM size its heap from the container's cgroup limits
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
