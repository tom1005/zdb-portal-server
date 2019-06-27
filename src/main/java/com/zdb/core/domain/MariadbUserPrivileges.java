package com.zdb.core.domain;

import java.util.List;

import lombok.Data;

@Data
public class MariadbUserPrivileges {

	private String user;
	private String host;
	private String grantee;
	private String password;
	private List<MariadbPrivileges> privileges;
	
	@Data
	public static class MariadbPrivileges{
		private String schema;
		private String select;
		private String insert;
		private String update;
		private String delete;
		private String execute;
	    private String showView;
		private String create;
		private String alter;
		private String references;
		private String index;
	    private String createView;
	    private String 	createRoutine;
	    private String alterRoutine;
		private String event;
		private String drop;
		private String trigger;
		private String grant;
	    private String createTmpTable;
		private String lockTables;		
	}
}

