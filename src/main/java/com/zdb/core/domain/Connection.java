package com.zdb.core.domain;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Connection {

	// master, slave
	String role;

	// public, private
	String connectionType;
	
	String serviceName;
	
	String ipAddress;
	
	int port;	
	
	String connectionString;
	
	String connectionLine;

}
