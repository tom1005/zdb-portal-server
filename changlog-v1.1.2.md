
=============================================================================================================
# v1.1.2
=============================================================================================================
```
use zdb;
drop table zdb.mariadbvariable;
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
  `editable` bit(1) NOT NULL,
  PRIMARY KEY (`category`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

```
=============================================================================================================

