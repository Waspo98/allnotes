FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY server/build/install/server /app/server
RUN chmod +x /app/server/bin/server
COPY composeApp/build/dist/wasmJs/productionExecutable /app/web
ENV PORT=8094
ENV DATA_DIR=/data
ENV WEB_DIR=/app/web
ENV WEB_DIST_DIR=/app/web
EXPOSE 8094
VOLUME ["/data"]
CMD ["/app/server/bin/server"]
