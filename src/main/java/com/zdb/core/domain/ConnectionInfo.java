package com.zdb.core.domain;

import java.util.ArrayList;
import java.util.List;

import com.zdb.core.controller.ZDBRestController;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ConnectionInfo
 * 
 * @author 06919
 *
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Slf4j
public class ConnectionInfo {

	private ZDBType dbType; 

	private String releaseName; 
	
	private String podName; 
	
//	private String domain;
	
	private int port;
	
	private String credetial;
	
	private String dbName;

	private List<Connection> connectionList ;	
	
	public ConnectionInfo(String serviceType) {
		ZDBType dbType = ZDBType.getType(serviceType);
		
		this.dbType = dbType;
	}
	
	public String getConnectionString(Connection conn) {
		String connectionString = new String();

	    switch (dbType) {
	    case MariaDB:
	    	connectionString = String.format("mysql://admin:[password]@%s:%d/%s", conn.getIpAddress(), conn.getPort(), getDbName());
	    	break;
	    case Redis:
	    	connectionString = String.format("redis://[password]@%s:%d", conn.getIpAddress(), conn.getPort());
	    	break;
	    default:
	    	break;
	    }			
		
		return connectionString;
	}
	
	public String getConnectionLine(Connection conn) {
		String connectionLine = new String();

	    switch (dbType) {
	    case MariaDB:
	    	connectionLine = String.format("mysql -u admin -p[password] --host %s --port %d", conn.getIpAddress(), conn.getPort());
	    	break;
	    case Redis:
	    	connectionLine = String.format("redis-cli -h %s -p %d -a [password]", conn.getIpAddress(), conn.getPort());
	    	break;
	    default:
	    	break;
	    }			
		
		return connectionLine;
	}	
	
}
