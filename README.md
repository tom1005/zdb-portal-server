# zdb-portal-server
zdb-portal-server

## Build & Dockerize
mvn -P ui clean install -Dmaven.test.skip=true
mvn -P agent clean install -Dmaven.test.skip=true
#mvn -P prod clean install -Dmaven.test.skip=true docker:build

sudo mvn -P prod clean install -Dmaven.test.skip=true -Dos.detected.classifier=osx-x86_64 -Dartifact.id=zdb-portal-server  docker:build

#bx cr login
docker image tag zdb-portal-server:<VERSION> registry.au-syd.bluemix.net/cloudzdb/zdb-portal-server:<VERSION>
docker image push registry.au-syd.bluemix.net/cloudzdb/zdb-portal-server:<VERSION>

## Deployment
kubectl delete deployment zdb-portal-server-deployment -n zdb-system
kubectl apply -f ./deploy/zdb-system-deployment.yml
