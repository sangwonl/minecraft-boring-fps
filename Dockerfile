FROM gradle:9-jdk25 AS builder
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
COPY resourcepack ./resourcepack
RUN gradle --no-daemon installDist

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /src/build/install/minestom-hello/ /app/
EXPOSE 25565
CMD ["/app/bin/minestom-hello"]
