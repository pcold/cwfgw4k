FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/cwfgw4k-all.jar app.jar

EXPOSE 8080

# Heap is sized at half the container memory cap so the JVM keeps generous
# room for non-heap (metaspace, JIT code cache, Netty direct buffers, JDBC
# buffers, thread stacks). 512m heap inside a 1Gi container is the safer
# side of the tradeoff for this workload — the bottleneck under load is
# typically JIT + native I/O space, not heap. The cap itself is set on the
# Cloud Run revision (--memory in cloudbuild.yaml).
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
