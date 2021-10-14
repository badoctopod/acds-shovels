FROM openjdk:11
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY target/acds-shovels.jar deploy/heroku/config.edn ./
CMD java -Dconf=config.edn -jar acds-http-api.jar
