FROM amazoncorretto:21.0.4 AS build
WORKDIR /app

COPY . .

RUN chmod +x ./gradlew && ./gradlew build -x test --no-daemon

FROM amazoncorretto:21.0.4 AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/server.jar
CMD ["java", "-jar", "/app/server.jar"]