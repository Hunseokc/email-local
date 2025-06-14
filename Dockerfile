# Dockerfile (수정안)

# 1. 빌드(Build) 단계: CPU 호환성이 높은 표준 JDK 이미지를 사용합니다.
FROM amazoncorretto:21.0.4 AS build
WORKDIR /app
# 프로젝트 전체 파일을 컨테이너로 복사합니다.
COPY . .

RUN chmod +x ./gradlew && ./gradlew build -x test --no-daemon

FROM amazoncorretto:21.0.4 AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/server.jar
CMD ["java", "-jar", "/app/server.jar"]