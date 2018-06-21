FROM java:8
VOLUME /tmp
ADD zdb-portal-server-1.0.0.jar zdb-portal-server.jar
RUN bash -c 'touch /zdb-portal-server.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Dspring.profiles.active=prod","-jar","/zdb-portal-server.jar"]