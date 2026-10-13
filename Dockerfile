FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/cwfgw4k-all.jar app.jar

EXPOSE 8080

# -Xmx is half the Cloud Run memory cap (--memory in
# .github/workflows/deploy.yml); the other half is the JVM's non-heap memory.
# Change the two together.
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
