# Python 3 Sandbox Runner for Mini LeetCode
# Base Image: python:3.10-alpine (Official Python 3.10 on Alpine Linux)
FROM python:3.10-alpine

# Prevent Python from writing .pyc files and enable unbuffered standard I/O
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Create non-root group and user judgeuser (UID 1000, GID 1000)
RUN addgroup -g 1000 -S judgeuser && \
    adduser -u 1000 -S judgeuser -G judgeuser

# Create workspace directory with appropriate ownership and permissions
WORKDIR /workspace
RUN chown -R judgeuser:judgeuser /workspace && \
    chmod 755 /workspace

# Switch to non-root user for safe sandbox execution
USER judgeuser
