FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY server/build/install/server /app/server
COPY composeApp/build/dist/wasmJs/productionExecutable /app/web
ENV PORT=8094
ENV DATA_DIR=/data
ENV WEB_DIST_DIR=/app/web
EXPOSE 8094
VOLUME ["/data"]
CMD ["/app/server/bin/server"]
