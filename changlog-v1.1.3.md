
=============================================================================================================
# chart 버전 관리를 위한 환경변수명 변경. configmap 수정
=============================================================================================================
As-Is 
  chart.mariadb.url: https://s3-api.us-geo.objectstorage.service.networklayer.com/zdb-chart-repo/mariadb-4.2.5.tgz
  chart.redis.url: https://s3-api.us-geo.objectstorage.service.networklayer.com/zdb-chart-repo/redis-3.6.5.tgz
To-Be
  zdb.mariadb.v10_2: https://s3-api.us-geo.objectstorage.service.networklayer.com/zdb-chart-repo/mariadb-4.2.4.tgz
  zdb.mariadb.v10_3: https://s3.private.us.cloud-object-storage.appdomain.cloud/zdb-chart-repo/mariadb-6.5.2.tgz
  zdb.redis.v4_0: https://s3-api.us-geo.objectstorage.service.networklayer.com/zdb-chart-repo/redis-3.6.5.tgz

=============================================================================================================
## configmap 
$ k -n zdb-system edit cm zdb-portal-server-config

  zdb.mariadb.v10_2: https://s3-api.us-geo.objectstorage.service.networklayer.com/zdb-chart-repo/mariadb-4.2.5.tgz
  zdb.mariadb.v10_3: https://s3.private.us.cloud-object-storage.appdomain.cloud/zdb-chart-repo/mariadb-6.5.2.tgz
  zdb.redis.v4_0: https://s3-api.us-geo.objectstorage.service.networklayer.com/zdb-chart-repo/redis-3.6.5.tgz
  zdb.mariadb.version: 10.3.16, 10.2.21, 10.2.14
  zdb.redis.version: 4.0.9  
  
## zdb-server-portal-deployment  
$ k -n zdb-system edit deploy zdb-portal-server-deployment

## 추가 ##
---------------------------------------------
        - name: zdb.mariadb.v10_2
          valueFrom:
            configMapKeyRef:
              key: zdb.mariadb.v10_2
              name: zdb-portal-server-config  
        - name: zdb.mariadb.v10_3
          valueFrom:
            configMapKeyRef:
              key: zdb.mariadb.v10_3
              name: zdb-portal-server-config  
        - name: zdb.redis.v4_0
          valueFrom:
            configMapKeyRef:
              key: zdb.redis.v4_0
              name: zdb-portal-server-config  
---------------------------------------------
## 삭제 ##
---------------------------------------------
        - name: chart.redis.url
          valueFrom:
            configMapKeyRef:
              key: chart.redis.url
              name: zdb-portal-server-config
        - name: chart.mariadb.url
          valueFrom:
            configMapKeyRef:
              key: chart.mariadb.url
              name: zdb-portal-server-config
---------------------------------------------              

## zdb-ui-portal-deployment              
$ k -n zdb-system edit deploy zdb-portal-ui-deployment
        - name: zdb.mariadb.version
          valueFrom:
            configMapKeyRef:
              key: zdb.mariadb.version
              name: zdb-portal-server-config  
        - name: zdb.redis.version
          valueFrom:
            configMapKeyRef:
              key: zdb.redis.version
              name: zdb-portal-server-config  
              

k -n zdb-system scale deploy zdb-portal-server-deployment --replicas 0
k -n zdb-system scale deploy zdb-portal-ui-deployment  --replicas 0
              
k -n zdb-system scale deploy zdb-portal-server-deployment --replicas 1
k -n zdb-system scale deploy zdb-portal-ui-deployment  --replicas 1              
=============================================================================================================



=============================================================================================================
# mariadb my.cnf 관리용 데이터 초기값 입력
=============================================================================================================

use zdb;
CREATE TABLE `mariadbvariable` (
  `category` varchar(64) NOT NULL,
  `name` varchar(64) NOT NULL,
  `alias` varchar(64) DEFAULT NULL,
  `default_value` longtext DEFAULT NULL,
  `dynamic` bit(1) NOT NULL,
  `enum_value_list` longtext DEFAULT NULL,
  `numeric_block_size` varchar(21) DEFAULT NULL,
  `numeric_max_value` varchar(21) DEFAULT NULL,
  `numeric_min_value` varchar(21) DEFAULT NULL,
  `variable_comment` longtext DEFAULT NULL,
  `variable_type` varchar(64) DEFAULT NULL,
  `data_type` varchar(255) DEFAULT NULL,
  `description` longtext DEFAULT NULL,
  `label` varchar(255) DEFAULT NULL,
  `value` varchar(255) DEFAULT NULL,
  `value_range` varchar(255) DEFAULT NULL,
  `editable` bit(1) NOT NULL default 1,
  PRIMARY KEY (`category`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

use zdb;
INSERT INTO zdb.mariadbvariable(category,name,alias,dynamic,default_value,variable_type,variable_comment,numeric_min_value,numeric_max_value,numeric_block_size,enum_value_list, editable)
SELECT 'mysqld', lower(variable_name),lower(variable_name),1,default_value,variable_type,variable_comment,numeric_min_value,numeric_max_value,numeric_block_size,enum_value_list, 1 as editable from INFORMATION_SCHEMA.SYSTEM_VARIABLES;

select * from zdb.mariadbvariable where name like '%char%';

select * from zdb.mariadbvariable where enum_value_list is not null;

# tx_isolation alias update 
update zdb.mariadbvariable set alias='transaction-isolation' where category='mysqld' and name = 'tx_isolation';
#skip_name_resolve
update zdb.mariadbvariable set alias='skip-name-resolve' where category='mysqld' and name = 'skip_name_resolve';
# default-time-zone
update zdb.mariadbvariable set alias='default-time-zone' where category='mysqld' and name = 'time_zone';
# plugin_dir
update zdb.mariadbvariable set default_value='/opt/bitnami/mariadb/plugin' where category='mysqld' and name = 'plugin_dir';
# pid_file
update zdb.mariadbvariable set alias='pid-file' where category='mysqld' and name = 'pid_file';

# character-set-client-handshake
update zdb.mariadbvariable set alias='character_set_client' where category='mysqld' and name = 'character_set_client';

update zdb.mariadbvariable set enum_value_list='utf8,euckr,utf8mb4,utf16' where category='mysqld' and name = 'character_set_client';

update zdb.mariadbvariable set enum_value_list='utf8,euckr,utf8mb4,utf16' where category='mysql' and name = 'default-character-set';
update zdb.mariadbvariable set enum_value_list='utf8,euckr,utf8mb4,utf16' where category='client' and name = 'default-character-set';

update zdb.mariadbvariable set enum_value_list='utf8,euckr,utf8mb4,utf16' where category='mysqld' and name = 'character_set_connection';
update zdb.mariadbvariable set enum_value_list='utf8,euckr,utf8mb4,utf16' where category='mysqld' and name = 'character_set_database';
update zdb.mariadbvariable set enum_value_list='utf8,euckr,utf8mb4,utf16' where category='mysqld' and name = 'character_set_server';
update zdb.mariadbvariable set enum_value_list='utf8,euckr,utf8mb4,utf16' where category='mysqld' and name = 'character_set_results';

update zdb.mariadbvariable set enum_value_list='utf8_general_ci,euckr_korean_ci,utf8mb4_general_ci,utf16_general_ci' where category='mysqld' and name = 'collation_connection';
update zdb.mariadbvariable set enum_value_list='utf8_general_ci,euckr_korean_ci,utf8mb4_general_ci,utf16_general_ci' where category='mysqld' and name = 'collation_database';
update zdb.mariadbvariable set enum_value_list='utf8_general_ci,euckr_korean_ci,utf8mb4_general_ci,utf16_general_ci' where category='mysqld' and name = 'collation_server';

commit;
select * from zdb.mariadbvariable where name like '%char%';
select * from zdb.mariadbvariable where name like '%collation%';

#[mysqld]
insert into  zdb.mariadbvariable (name, alias, category, dynamic , default_value, enum_value_list, variable_type, editable) values ('character-set-client-handshake', 'character-set-client-handshake','mysqld',0,'OFF','OFF,ON','BOOLEAN',1 );

#[client]
#default-character-set
insert into  zdb.mariadbvariable (name, alias, category, dynamic , default_value, variable_comment, variable_type) values ('default-character-set', 'default-character-set','client',1,'utf8','The default character set', 'ENUM');
insert into  zdb.mariadbvariable (name, alias, category, dynamic , default_value, variable_type, editable) values ('port', 'port','client',0,'3306','VARCHAR', 0);
insert into  zdb.mariadbvariable (name, alias, category, dynamic , default_value, variable_type) values ('socket', 'socket','client',0,'/opt/bitnami/mariadb/tmp/mysql.sock','VARCHAR');

#[mysql]
# no-auto-rehash
insert into  zdb.mariadbvariable (name, alias, category, dynamic , default_value, variable_comment, variable_type, editable) values ('no-auto-rehash', 'no-auto-rehash','mysql',0,'','No automatic rehashing. One has to use \'rehash\' to get table and field completion. This gives a quicker start of mysql and disables rehashing on reconnect.','VARCHAR', 0);
#max_allowed_packet
insert into  zdb.mariadbvariable (name, alias, category, data_type,description,dynamic,label,value,value_range,default_value,enum_value_list,numeric_block_size,numeric_max_value,numeric_min_value,variable_comment,variable_type) 
SELECT name, alias, 'mysql' as category, data_type,description, dynamic,label,value,value_range,default_value,enum_value_list,numeric_block_size,numeric_max_value,numeric_min_value,variable_comment,variable_type  FROM zdb.mariadbvariable a where category='mysqld' and name='max_allowed_packet' ;
insert into  zdb.mariadbvariable (name, alias, category, dynamic , default_value, variable_comment, variable_type) values ('default-character-set', 'default-character-set','mysql',1,'utf8','The default character set', 'ENUM');


#[mysqldump]
# quick
insert into  zdb.mariadbvariable (name, alias, category, dynamic , default_value, variable_type, editable) values ('quick', 'quick','mysqldump',0,'','VARCHAR', 0);


#max_allowed_packet
insert into  zdb.mariadbvariable (name, alias, category, data_type,description,dynamic,label,value,value_range,default_value,enum_value_list,numeric_block_size,numeric_max_value,numeric_min_value,variable_comment,variable_type) 
SELECT name, alias, 'mysqldump' as category, data_type,description, dynamic,label,value,value_range,default_value,enum_value_list,numeric_block_size,numeric_max_value,numeric_min_value,variable_comment,variable_type  FROM zdb.mariadbvariable a where category='mysqld' and name='max_allowed_packet' ;

#[mysqld_safe]
#error_log
insert into  zdb.mariadbvariable (name, alias, category, data_type,description,dynamic,label,value,value_range,default_value,enum_value_list,numeric_block_size,numeric_max_value,numeric_min_value,variable_comment,variable_type, editable) 
SELECT name, alias, 'mysqld_safe' as category, data_type,description, 0 as dynamic,label,'/bitnami/mariadb/logs/mysql_error.log' as value,value_range, '/bitnami/mariadb/logs/mysql_error.log' as default_value,enum_value_list,numeric_block_size,numeric_max_value,numeric_min_value,variable_comment,variable_type, 1 as editable FROM zdb.mariadbvariable a where category='mysqld' and name='log_error' ;


DELETE FROM `zdb`.`mariadbvariable` WHERE (`category` = 'mysqld') and (`name` = 'innodb_undo_tablespaces');
DELETE FROM `zdb`.`mariadbvariable` WHERE (`category` = 'client') and (`name` = 'port');
DELETE FROM `zdb`.`mariadbvariable` WHERE (`category` = 'mysqld') and (`name` = 'port');

commit;

=============================================================================================================
=============================================================================================================
# Performace Storage를 위한 변수 추가 및 Chart 변경
=============================================================================================================

1. com.zdb.core.domain.ZDBEntity
private String kindOfStorage; 추가

2. com.zdb.core.domain.PersistenceSpec
private String storageIops; 추가

3. com.zdb.core.service.MariaDBInstaller
String masterIops 추가

=============================================================================================================