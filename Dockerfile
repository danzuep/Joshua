# FROM openjdk:21-jdk-slim
FROM azul/zulu-openjdk-alpine:11-latest
VOLUME /tmp
ARG JAVA_OPTS
ENV JAVA_OPTS=$JAVA_OPTS
COPY joshua.jar joshua.jar
EXPOSE 8080 5005
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar joshua.jar"]
# For Spring-Boot project, use the entrypoint below to reduce Tomcat startup time.
#ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar joshua.jar"]
# For Java-specific issues, enable the JVM flags for better diagnostics, as shown in the following example:
#ENTRYPOINT ["java", "-XX:+PrintFlagsFinal", "-XX:+PrintGCDetails", "-jar", "app.jar"]