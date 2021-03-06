package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserPrivilegesForSchema {
	String user;
	String password;
	String host;
	String schema;
	String select;
	String insert;
	String update;
	String delete;
	String execute;
	String showView;
	String create;
	String alter;
	String references;
	String index;
	String createView;
	String createRoutine;
	String alterRoutine;
	String event;
	String drop;
	String trigger;
	String grant;
	String createTmpTable;
	String lockTables;
	String createUser;
	
	String grantee; 
	String tableCatalog; 
	String tableSchema; 
	String privilegeType; 
	String isGrantable;
	String grantOption;
	String createTemporaryTables;
}
