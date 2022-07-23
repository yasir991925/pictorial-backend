#FROM gradle:7.5.0-jdk18-alpine
#
#ENV HOME .
#ENV MAIN_VERTICLE com.pictorial.MainVerticle
#ENV VERTICLE_FILE build/libs/server-1.0.0-fat.jar
#
#ENV APP_HOME=/usr/app/
#WORKDIR $APP_HOME
#
#COPY build.gradle.kts settings.gradle.kts $APP_HOME
#
#COPY gradle $APP_HOME/gradle
#COPY --chown=gradle:gradle . /home/gradle/src
#USER root
#RUN chown -R gradle /home/gradle/src
#
#RUN gradle build
#COPY . .
#
#RUN gradle run
#
#ENTRYPOINT ["sh", "-c"]
#CMD ["exec java -jar $VERTICLE_FILE"]


FROM vertx/vertx4

ENV VERTICLE_NAME com.pictorial.MainVerticle
ENV VERTICLE_FILE build/libs/server-1.0.0-fat.jar

ENV VERTICLE_HOME /usr/verticles

COPY $VERTICLE_FILE $VERTICLE_HOME/

WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec vertx run $VERTICLE_NAME -cp $VERTICLE_HOME/*"]
