FROM amazoncorretto:21.0.5
COPY /target/light-scheduler.jar server.jar
CMD ["/bin/sh","-c","exec java -Dlight-4j-config-dir=/config -Dlogback.configurationFile=/config/logback.xml -jar /server.jar"]
