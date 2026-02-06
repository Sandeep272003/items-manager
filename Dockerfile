# Use a Java 17 base image
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy jar (built by Maven)
COPY target/item-manager-1.0.0.jar app.jar

# Create data dir (container volume can be mounted here)
RUN mkdir -p /app/data

# Expose port
EXPOSE 8080

# Run backend only (do not launch Swing in container)
ENV DESKTOP=false

ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]
