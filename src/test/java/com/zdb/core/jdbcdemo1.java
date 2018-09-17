package com.zdb.core;
import org.mariadb.jdbc.Driver;
 
import java.sql.ResultSet;
import java.util.Properties;
import java.sql.Connection;
import java.sql.SQLException;
 
public class jdbcdemo1 {
  public static void main(String[] args) throws Exception {
    Driver driver = new Driver();
    Properties props = new Properties();
    ResultSet rs;
    String variable_value;
    Connection conn = null;
    String JDBC_URL = "jdbc:mysql:replication://address=(host=169.56.87.218)(port=3306)(type=master),"
    				+ "address=(host=169.56.87.208)(port=3306)(type=slave)";
//    String JDBC_URL = "jdbc:mysql:replication://address=(host=169.56.76.138)(port=3306)(type=master),"
//    		+ "address=(host=169.56.70.221)(port=3306)(type=slave)";http://169.56.87.208
 
    
//    169.56.70.221:3306
    
    props.put("retriesAllDown","5");
    props.put("user", "root");
    props.put("password", "zdb12#$");
    props.put("user", "admin");
    props.put("password", "unt40lyv3r");
 
    System.out.println("\n------------ MariaDB Connector/J and MariaDB Replication Testing ------------\n");
 
    System.out.println("Trying connection...");
    try {
    	conn = driver.connect(JDBC_URL, props);
    	}
    catch (SQLException e) {
    	System.out.println("Connection Failed!");
    	System.out.println("Error cause: "+e.getCause());
    	System.out.println("Error message: "+e.getMessage());
    	return;
    }
 
    System.out.println("Connection established...");
 
    for(int i=1; i <= 1000; i++) {
 
// Read write query that can be performed ONLY on master server
 
        System.out.println("\nQuery "+i+": ");
    	// Fake Read Write SQL statement (just to see where it goes)
    	System.out.println("Read Write query...");
    	try {
    		conn.setReadOnly(false);
    	}
    	catch (SQLException e) {
    		System.out.println("Connection read write property set has failed...");
    	}
 
    	if (conn.isReadOnly() == false) {
    		try {
    			rs = conn.createStatement().executeQuery("SELECT variable_value FROM information_schema.global_variables " +
                                                                 "WHERE variable_name='hostname'");
    			while (rs.next()) {
    				variable_value = rs.getString("variable_value");
    				System.out.println("variable_value : " + variable_value);
    			}
    			conn.createStatement().executeUpdate("insert into ppp.aaaa values('id_"+i+"','name_"+i+"')");
    			conn.commit();
    		}
    		catch (SQLException e) {
    			System.out.println("Read/write query has failed...");
    		}
    	}
 
// Read Only statement (that can also be done on master server if all slaves are down)
    	System.out.println("Read Only query...");
    	try {
    		conn.setReadOnly(true);	
    	}
    	catch (SQLException e) {
    		System.out.println("Connection read only property set has failed...");
    	}
   		try {
   			rs = conn.createStatement().executeQuery("SELECT variable_value FROM information_schema.global_variables  WHERE variable_name='hostname'");
   			while (rs.next()) {
   				variable_value = rs.getString("variable_value");
   				System.out.println("variable_value : " + variable_value);
   				}
   			conn.createStatement().executeUpdate("insert into ppp.aaaa values('id_"+i+"','name_"+i+"')");
			conn.commit();	
   		}
   		catch (SQLException e) {
   			System.out.println("Read only query has failed...");
   			}
 
    	Thread.sleep(2000);
    }
    conn.close();
  }
}
