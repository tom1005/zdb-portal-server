# zdb-portal-server
zdb-portal-server

## Build & Dockerize
```
mvn -P ui clean install -Dmaven.test.skip=true
mvn -P agent clean install -Dmaven.test.skip=true
#mvn -P prod clean install -Dmaven.test.skip=true docker:build
sudo mvn -P prod clean install -Dmaven.test.skip=true -Dos.detected.classifier=osx-x86_64 -Dartifact.id=zdb-portal-server  docker:build

#bx cr login
docker image tag zdb-portal-server:<VERSION> registry.au-syd.bluemix.net/cloudzdb/zdb-portal-server:<VERSION>
docker image push registry.au-syd.bluemix.net/cloudzdb/zdb-portal-server:<VERSION>

docker build -t registry.au-syd.bluemix.net/cloudzdb/zdb-portal-server:1.2.1 ./

docker image tag zdb-portal-server:1.2.1 registry.au-syd.bluemix.net/cloudzdb/zdb-portal-server:1.2.1
docker image push registry.au-syd.bluemix.net/cloudzdb/zdb-portal-server:1.2.1

## Deployment
kubectl delete deployment zdb-portal-server-deployment -n zdb-system
kubectl apply -f ./deploy/zdb-system-deployment.yml
```


```
version=1.2.1-aks

echo $(date "+%Y-%m-%d %H:%M:%S") : Build start
echo '# zdb-portal-server & zdb-portal-ui build/deploy version - '${version}

echo '# db-portal-server-for-ui build'
cd  /Users/a06919/github/zdb-portal-server
echo '# git pull'
#/usr/bin/git pull
mvn clean install -P ui -Dos.detected.classifier=osx-x86_64 -Dartifact.id=zdb-portal-server-for-ui -Dmaven.test.skip=true

echo '# zdb-portal-server-build'
mvn clean package -P prod -Dos.detected.classifier=osx-x86_64 -Dartifact.id=zdb-portal-server -Dmaven.test.skip=true  docker:build

echo '# zdb-portal-ui build'
cd /Users/a06919/github/zdb-portal-ui
echo '# git pull'
#/usr/bin/git pull
mvn clean package -Dos.detected.classifier=osx-x86_64 -Dmaven.test.skip=true docker:build

echo '# docker tag zdb-portal-ui:'${version}
docker image tag zdb-portal-ui registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-ui:1.2.1-aks

echo '# docker image push zdb-portal-ui:'1.2.1-aks
docker image push registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-ui:1.2.1-aks

echo '# docker tag zdb-portal-server:'1.2.1-aks
docker image tag zdb-portal-server registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-server:1.2.1-aks
echo '# docker tag zdb-portal-server:'1.2.1-aks
docker image push registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-server:1.2.1-aks

#export KUBECONFIG=/home/zservice/.bluemix/plugins/container-service/clusters/zcp-cbt-admin/kube-config-seo01-zcp-cbt.yml


/usr/local/bin/kubectl config view

echo '# zdb-portal-server-deployment --replicas 0'
/usr/local/bin/kubectl -n zdb-system scale deploy zdb-portal-server-deployment --replicas 0

echo '# change set image zdb-portal-server-deployment'
/usr/local/bin/kubectl -n zdb-system set image deployment.v1beta1.extensions/zdb-portal-server-deployment zdb-portal-server=registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-server:1.2.1-aks

echo '# zdb-portal-ui-deployment --replicas 0'
/usr/local/bin/kubectl -n zdb-system scale deploy zdb-portal-ui-deployment --replicas 0

echo '# change set image zdb-portal-ui-deployment'
/usr/local/bin/kubectl -n zdb-system set image deployment.v1beta1.extensions/zdb-portal-ui-deployment zdb-portal-ui=registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-ui:1.2.1-aks

sleep  2

echo '# zdb-portal-ui-deployment --replicas 1'
/usr/local/bin/kubectl -n zdb-system scale deploy zdb-portal-ui-deployment --replicas 1

echo '# zdb-portal-server-deployment --replicas 1'
/usr/local/bin/kubectl -n zdb-system scale deploy zdb-portal-server-deployment --replicas 1

echo $(date "+%Y-%m-%d %H:%M:%S") : Build end
```