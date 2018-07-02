# zdb-portal-server
zdb-portal-server

## Build & Dockerize
mvn -P ui clean install -Dmaven.test.skip=true
mvn -P prod clean install -Dmaven.test.skip=true docker:build

#bx cr login

docker image tag zdb-portal-server:latest registry.au-syd.bluemix.net/zdb-dev/zdb-portal-server:latest
docker image push registry.au-syd.bluemix.net/zdb-dev/zdb-portal-server:latest

## Deployment
kubectl delete deployment zdb-portal-server-deployment -n zdb-system
kubectl apply -f ./deploy/zdb-system-deployment.yml
