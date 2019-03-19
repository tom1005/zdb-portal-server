package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class DBUser {
	String user;
	String password;
	String host;
	String select;
	String insert;
	String update;
	String delete;
	String execute;
	String create;
	String alter;
	String drop;
	String createView;
	String trigger;
	String grant;
	String createUser;
}
