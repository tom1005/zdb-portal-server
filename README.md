# zdb-portal-server
zdb-portal-server

## Build & Dockerize
```
mvn -P ui clean install -Dmaven.test.skip=true
mvn -P agent clean install -Dmaven.test.skip=true
mvn -P prod clean install -Dmaven.test.skip=true -Dos.detected.classifier=osx-x86_64 -Dartifact.id=zdb-portal-server  docker:build

#bx cr login

```


```
version=1.2.1-native

echo $(date "+%Y-%m-%d %H:%M:%S") : Build start
echo '# zdb-portal-server & zdb-portal-ui build/deploy version - '${version}

echo '# db-portal-server-for-ui build'
cd  ~/github/zdb-portal-server
echo '# git pull'
#/usr/bin/git pull
mvn clean install -P ui -Dos.detected.classifier=osx-x86_64 -Dartifact.id=zdb-portal-server-for-ui -Dmaven.test.skip=true

cd  ~/github/zdb-portal-server
echo '# zdb-portal-server-build'
mvn clean package -P prod -Dos.detected.classifier=osx-x86_64 -Dartifact.id=zdb-portal-server -Dmaven.test.skip=true  docker:build

echo '# zdb-portal-ui build'
cd ~/github/zdb-portal-ui

mvn clean package -Dos.detected.classifier=osx-x86_64 -Dmaven.test.skip=true docker:build

echo '# docker tag zdb-portal-ui:'${version}
docker image tag zdb-portal-ui registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-ui:1.2.1-native

echo '# docker image push zdb-portal-ui:'1.2.1-native
docker image push registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-ui:1.2.1-native

echo '# docker tag zdb-portal-server:'1.2.1-native
docker image tag zdb-portal-server registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-server:1.2.1-native
echo '# docker tag zdb-portal-server:'1.2.1-native
docker image push registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-server:1.2.1-native

#export KUBECONFIG=


/usr/local/bin/kubectl config view

echo '# zdb-portal-server-deployment --replicas 0'
/usr/local/bin/kubectl -n zdb-system scale deploy zdb-portal-server-deployment --replicas 0

echo '# change set image zdb-portal-server-deployment'
/usr/local/bin/kubectl -n zdb-system set image deployment.v1beta1.extensions/zdb-portal-server-deployment zdb-portal-server=registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-server:1.2.1-native

echo '# zdb-portal-ui-deployment --replicas 0'
/usr/local/bin/kubectl -n zdb-system scale deploy zdb-portal-ui-deployment --replicas 0

echo '# change set image zdb-portal-ui-deployment'
/usr/local/bin/kubectl -n zdb-system set image deployment.v1beta1.extensions/zdb-portal-ui-deployment zdb-portal-ui=registry.au-syd.bluemix.net/cloudzdb/dev/zdb-portal-ui:1.2.1-native

sleep  2

echo '# zdb-portal-ui-deployment --replicas 1'
/usr/local/bin/kubectl -n zdb-system scale deploy zdb-portal-ui-deployment --replicas 1

echo '# zdb-portal-server-deployment --replicas 1'
/usr/local/bin/kubectl -n zdb-system scale deploy zdb-portal-server-deployment --replicas 1

echo $(date "+%Y-%m-%d %H:%M:%S") : Build end
```