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

		if("unknown".equals(conn.getIpAddress())) {
			return "EXTERNAL-IP가 존재하지 않습니다. 관리자에게 문의하세요.";
		}
	    switch (dbType) {
	    case MariaDB:
	    	// jdbc:mariadb://169.56.77.196:3306/zdb
	    	connectionString = String.format("jdbc:mariadb://%s:%d/%s", conn.getIpAddress(), conn.getPort(), getDbName());
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
		if("unknown".equals(conn.getIpAddress())) {
			return "EXTERNAL-IP가 존재하지 않습니다. 관리자에게 문의하세요.";
		}
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
