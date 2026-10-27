# Java 17 Sandbox Runner for Mini LeetCode
# Base Image: eclipse-temurin:17-alpine (Official OpenJDK 17 on Alpine Linux)
FROM eclipse-temurin:17-alpine

# Create non-root group and user judgeuser (UID 1000, GID 1000)
RUN addgroup -g 1000 -S judgeuser && \
    adduser -u 1000 -S judgeuser -G judgeuser

# Create workspace directory with appropriate ownership and permissions
WORKDIR /workspace
RUN chown -R judgeuser:judgeuser /workspace && \
    chmod 755 /workspace

# Switch to non-root user for safe sandbox execution
USER judgeuser
