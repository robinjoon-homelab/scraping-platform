FROM eclipse-temurin:25-jdk-noble AS trust-tools
COPY scripts/security/RuntimeTrust.java /tmp/RuntimeTrust.java
RUN javac --release 25 -d /trust-tools /tmp/RuntimeTrust.java

FROM eclipse-temurin:25-jre-noble

# Run again whenever the base or mitigation changes; keep unrelated build caches.
COPY --from=trust-tools /trust-tools /tmp/trust-tools
RUN java -cp /tmp/trust-tools RuntimeTrust sanitize \
    && java -cp /tmp/trust-tools RuntimeTrust roots \
    && rm -rf /tmp/trust-tools

WORKDIR /app
RUN mkdir -p /app/data && chown 10001:10001 /app/data
COPY --chown=10001:10001 build/libs/application.jar application.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
