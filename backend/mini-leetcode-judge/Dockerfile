FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install docker-cli so judge worker can coordinate ephemeral sandbox containers
RUN apk add --no-cache docker-cli curl

COPY mini-leetcode-judge/target/mini-leetcode-judge-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
