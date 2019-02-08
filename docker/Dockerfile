FROM java:8
ENV SPRING_ACTIVE_PROFILE prod
ENV APP_VERSION 1.0.5
ADD dist/zdb-portal-server-1.0.5.jar zdb-portal-server-1.0.5.jar
VOLUME /tmp
VOLUME /target
RUN apt-get update
RUN apt-get install -y --no-install-recommends python-software-properties
RUN apt-get install -y --no-install-recommends percona-toolkit
RUN bash -c 'touch /zdb-portal-server-1.0.5.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Dspring.profiles.active=${SPRING_ACTIVE_PROFILE}","-jar","/zdb-portal-server-1.0.5.jar"]
