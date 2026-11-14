# The league server's image. It holds a Java runtime and the server's jars, and nothing that builds them:
# run `./gradlew :server:installDist` first, then `docker build -t flyway-bracket-server server`
# (docs/server/running.md). No state pack is ever in the image: the packs come in as a volume, at the path
# PACKS_DIR names.
FROM eclipse-temurin:25-jre-alpine

# The server needs no rights on the machine, so it does not run as root.
RUN addgroup -S -g 10001 server && adduser -S -u 10001 -G server -H -s /sbin/nologin server

COPY build/install/server /opt/server

USER 10001:10001
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/opt/server/bin/server"]
