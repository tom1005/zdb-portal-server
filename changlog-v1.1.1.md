
=============================================================================================================
# v1.1.1
=============================================================================================================
## 추가 기능
```
- mariadb 프로세스관리
- 알람 설정 관리

```
# 업그레이드 사전 작업
- disk_usage, release_meta_data, slave_status 테이블 구조 변경으로 테이블 삭제 후 zdb-portal-server:1.1.1 업그레이드
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
=============================================================================================================

