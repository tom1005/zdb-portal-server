
=============================================================================================================
# v1.1.0
=============================================================================================================
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
  
=============================================================================================================

