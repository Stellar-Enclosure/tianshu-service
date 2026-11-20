FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/stellar-enclosure-tianshu-admin-exec.jar app.jar
RUN mkdir -p /opt/stellar \
    && java -Dloader.main=top.egon.cola.component.common.id.probe.WorkerProcessProbe \
        -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher write-script > /opt/stellar/probe \
    && chmod 0555 /opt/stellar/probe

EXPOSE 18080 19080
ENTRYPOINT ["java","-jar","/app/app.jar"]
