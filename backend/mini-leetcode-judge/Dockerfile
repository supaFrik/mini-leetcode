FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Install docker-cli so judge worker can coordinate ephemeral sandbox containers
RUN apk add --no-cache docker-cli curl

# Thư mục làm việc của các phiên console OOP. Mount thư mục này từ HOST để
# sandbox container (do Docker daemon trên HOST tạo) nhìn thấy được file .class.
RUN mkdir -p /app/temp-console

COPY mini-leetcode-judge/target/mini-leetcode-judge-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
