FROM adoptopenjdk:8u262-b10-jre-hotspot as builder
WORKDIR application
ARG JAR_FILE=build/libs/ili2gpkg-web-service.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM adoptopenjdk:8u262-b10-jre-hotspot
WORKDIR application
COPY --from=builder application/dependencies/ ./
COPY --from=builder application/spring-boot-loader/ ./
COPY --from=builder application/snapshot-dependencies/ ./
COPY --from=builder application/application ./

ENTRYPOINT ["java" ,"-XX:MaxRAMPercentage=80.0", "-noverify", "-XX:TieredStopAtLevel=1", "org.springframework.boot.loader.JarLauncher"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD curl http://localhost:8080/actuator/health
