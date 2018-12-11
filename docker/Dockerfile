FROM java:8
ENV SPRING_ACTIVE_PROFILE prod
ENV APP_VERSION=1.0.4
ADD dist/zdb-portal-server-${APP_VERSION}.jar zdb-portal-server-${APP_VERSION}.jar
VOLUME /tmp
VOLUME /target
RUN bash -c 'touch /zdb-portal-server-${APP_VERSION}.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Dspring.profiles.active=${SPRING_ACTIVE_PROFILE}","-jar","/zdb-portal-server-${APP_VERSION}.jar"]
