FROM java:8
ENV SPRING_ACTIVE_PROFILE prod
ADD dist/zdb-portal-server-1.0.0.jar zdb-portal-server.jar
VOLUME /tmp
VOLUME /target
RUN bash -c 'touch /zdb-portal-server.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Dspring.profiles.active=prod","-jar","/zdb-portal-server.jar"]