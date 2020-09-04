FROM adoptopenjdk:8u262-b10-jre-hotspot as builder
WORKDIR /home/application
ARG JAR_FILE=build/libs/ili2gpkg-web-service*.jar
COPY ${JAR_FILE} /home/application.jar
RUN java -Djarmode=layertools -jar /home/application.jar extract

FROM adoptopenjdk:8u262-b10-jre-hotspot
EXPOSE 8080
WORKDIR /home/application
COPY --from=builder /home/application/dependencies/ ./
COPY --from=builder /home/application/spring-boot-loader/ ./
COPY --from=builder /home/application/snapshot-dependencies/ ./
#COPY --from=builder /home/application/application/ ./

RUN chown -R 1001:0 /home/application && \
    chmod -R g=u /home/application

USER 1001

ENTRYPOINT ["java" ,"-XX:MaxRAMPercentage=80.0", "-noverify", "-XX:TieredStopAtLevel=1", "org.springframework.boot.loader.JarLauncher"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD curl http://localhost:8080/actuator/health
