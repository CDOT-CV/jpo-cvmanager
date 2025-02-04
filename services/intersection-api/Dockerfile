# # === Build image for Java ===
FROM maven:3.8-eclipse-temurin-21-alpine as jbuilder

WORKDIR /home

ARG MAVEN_GITHUB_TOKEN
ARG MAVEN_GITHUB_ORG

ENV MAVEN_GITHUB_TOKEN=$MAVEN_GITHUB_TOKEN
ENV MAVEN_GITHUB_ORG=$MAVEN_GITHUB_ORG

COPY ./api/pom.xml ./intersection-api/
COPY ./api/settings.xml ./intersection-api/

WORKDIR /home/intersection-api
RUN mvn -s settings.xml dependency:resolve

COPY ./api/src ./src
RUN mvn -s settings.xml install -DskipTests

# # === RUNTIME IMAGE for Java and ACM ===
# # Use Amazon Corretto Java on Amazon Linum 2023 to match the codec build env
# # FROM alpine:3.12
FROM amazoncorretto:21-al2023

WORKDIR /home

COPY --from=jbuilder /home/intersection-api/src/main/resources/application.yaml /home
COPY --from=jbuilder /home/intersection-api/src/main/resources/logback.xml /home
COPY --from=jbuilder /home/intersection-api/target/intersection-api.jar /home

USER root

ENTRYPOINT ["java", \
    "-Dlogback.configurationFile=/home/logback.xml", \
    "-jar", \
    "/home/intersection-api.jar"]

# Not using JMX
#    "-Djava.rmi.server.hostname=$DOCKER_HOST_IP", \
#    "-Dcom.sun.management.jmxremote.port=9090", \
#    "-Dcom.sun.management.jmxremote.rmi.port=9090", \
#    "-Dcom.sun.management.jmxremote", \
#    "-Dcom.sun.management.jmxremote.local.only=true", \
#    "-Dcom.sun.management.jmxremote.authenticate=false", \
#    "-Dcom.sun.management.jmxremote.ssl=false", \