
use zdb;
INSERT INTO zdb.mariadbvariable(category,name,alias,dynamic,default_value,variable_type,variable_comment,numeric_min_value,numeric_max_value,numeric_block_size,enum_value_list, editable)
SELECT 'mysqld', lower(variable_name),lower(variable_name),1,default_value,variable_type,variable_comment,numeric_min_value,numeric_max_value,numeric_block_size,enum_value_list, 1 as editable from INFORMATION_SCHEMA.SYSTEM_VARIABLES;

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

#[mysqld]


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

commit;
