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

docker build -t registry.au-syd.bluemix.net/cloudzdb/zdb-portal-server:1.1.0 ./

docker image tag zdb-portal-server:1.1.0 registry.au-syd.bluemix.net/cloudzdb/zdb-portal-server:1.1.0
docker image push registry.au-syd.bluemix.net/cloudzdb/zdb-portal-server:1.1.0

## Deployment
kubectl delete deployment zdb-portal-server-deployment -n zdb-system
kubectl apply -f ./deploy/zdb-system-deployment.yml

===============================
# v1.1.1
## 추가 기능
```
- mariadb 프로세스관리
- 알람 설정 관리

```
# 업그레이드 사전 작업
- disk_usage 테이블 구조 변경으로 테이블 삭제 후 zdb-portal-server:1.1.1 업그레이드
```
kubectl -n zdb-system -it exec zdb-system-zdb-mariadb-0 -- /bin/bash -c "mysql -uzdb -p -e 'drop table zdb.disk_usage;'"
kubectl -n zdb-system -it exec zdb-system-zdb-mariadb-0 -- /bin/bash -c "mysql -uzdb -p -e 'drop table zdb.release_meta_data;'"
kubectl -n zdb-system -it exec zdb-system-zdb-mariadb-0 -- /bin/bash -c "mysql -uzdb -p -e 'drop table zdb.slave_status;'"
kubectl -n zdb-system -it exec zdb-system-zdb-mariadb-0 -- /bin/bash -c "mysql -uzdb -p -e 'use zdb;show tables;'"

or

kubectl -n zdb-system -it exec zdb-portal-db-mariadb-0 -- /bin/bash -c "mysql -uzdb -p -e 'drop table zdb.disk_usage;'"
kubectl -n zdb-system -it exec zdb-portal-db-mariadb-0 -- /bin/bash -c "mysql -uzdb -p -e 'drop table zdb.release_meta_data;'"
kubectl -n zdb-system -it exec zdb-portal-db-mariadb-0 -- /bin/bash -c "mysql -uzdb -p -e 'drop table zdb.slave_status;'"
kubectl -n zdb-system -it exec zdb-portal-db-mariadb-0 -- /bin/bash -c "mysql -uzdb -p -e 'use zdb;show tables;'"
```



# v1.1.0
## 환경변수 추가 :
 
### zdb-portal-server-config configmap 에 추가.
 - chart.antiAffinity: hard 
 
### zdb-portal-server-deployment env 추가 
```
      - env:
        - name: chart.antiAffinity
          valueFrom:
            configMapKeyRef:
              key: chart.antiAffinity
              name: zdb-portal-server-config
```

```
제품명 - Cloud Z DB
버전명 - v1.1.0
릴리즈 일자 - 2019/04/01
릴리즈 내용
- MariaDB 서비스 failover/failback 기능
- MariaDB Auto failover
- MariaDB 10.2.21 추가
- MariaDB 서비스 포트 변경 기능
- MariaDB 사용자 관리 기능
- MariaDB Database 관리 기능
- MariaDB 백업 기능 개선 및 증분백업, 복원 기능

기타 버그 픽스
```

        