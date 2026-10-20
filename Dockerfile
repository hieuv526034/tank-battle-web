# Wrapper pin Gradle 8.14.6; build chỉ biên dịch, không khởi động Spring/Flyway.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Tách dependency khỏi source để Render/Docker tái sử dụng cache khi chỉ sửa Java.
COPY gradlew ./gradlew
COPY gradle ./gradle
COPY settings.gradle build.gradle gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon --console=plain copyPostgresDriver
COPY src ./src
# CI đã chạy test; bước này đóng gói lại cùng source, không nhận credentials production.
RUN ./gradlew --no-daemon --console=plain bootJar

# Runtime chỉ có JRE và JAR; không mang Gradle, source, test DB hoặc .env vào image.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --gid 1000 tankbattle \
    && useradd --uid 1000 --gid 1000 --no-create-home --shell /usr/sbin/nologin tankbattle
COPY --from=build --chown=1000:1000 /workspace/build/libs/tank-battle-web-1.0.0.jar ./app.jar
USER 1000:1000

# Container mặc định chạy PostgreSQL/prod kể cả khi Dashboard chưa chọn profile.
ENV SPRING_PROFILES_ACTIVE=prod
# Render Free có RAM hạn chế; chừa bộ nhớ ngoài heap cho thread, metaspace và native I/O.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:InitialRAMPercentage=15.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
# Exec-form để Java nhận SIGTERM trực tiếp, lưu checkpoint trong graceful shutdown.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
