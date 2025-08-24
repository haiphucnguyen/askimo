############################
# Stage 1: Build native bin
############################
FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /src

# Optional: speed up Gradle using BuildKit cache mounts
# (works on GitHub Actions with docker/build-push-action)
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -Dorg.gradle.jvmargs="-Xmx2g" -Pci=true nativeCompile

############################
# Stage 2: Minimal runtime
############################
FROM gcr.io/distroless/cc-debian12:nonroot
WORKDIR /work
COPY --chown=nonroot:nonroot --from=build /src/build/native/nativeCompile/askimo /usr/local/bin/askimo
ENTRYPOINT ["/usr/local/bin/askimo"]
