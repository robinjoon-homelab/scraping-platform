FROM eclipse-temurin:25-jre-noble

WORKDIR /app
RUN mkdir -p /app/data && chown 10001:10001 /app/data
COPY --chown=10001:10001 build/libs/application.jar application.jar

USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
