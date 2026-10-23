FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/stellar-enclosure-tianshu-admin-exec.jar app.jar
EXPOSE 18080 19080
ENTRYPOINT ["java","-jar","/app/app.jar"]
