# https://mariadb.com/kb/en/library/server-system-variables/

INSERT INTO zdb.mariadbvariable (name,alias,category,data_type,description,dynamic,label,value,value_range) VALUES ( 'tx_isolation', 'transaction-isolation', 'mysqld', 'enumeration', 'The transaction isolation level. See also SET TRANSACTION ISOLATION LEVEL.', true, 'tx_isolation', 'REPEATABLE-READ', 'READ-UNCOMMITTED, READ-COMMITTED, REPEATABLE-READ, SERIALIZABLE');