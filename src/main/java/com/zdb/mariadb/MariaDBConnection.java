package com.zdb.mariadb;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.repository.ZDBMariaDBAccountRepository;
import com.zdb.core.util.K8SUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * A class to control mariadb variables
 * 
 * @author nojinho@bluedigm.com
 *
 */
@Slf4j
public class MariaDBConnection {
	private static final Logger logger = LoggerFactory.getLogger(MariaDBConnection.class);
	
	private Connection connection = null;
    private Statement statement = null;
    
    @Inject protected ZDBMariaDBAccountRepository accountRepository;
    

    /**
     * Constructor of MariaDBConnection. close() function must be called.
     * @param url MariaDB URL
     * @param user User ID
     * @param password User Password
     * @throws SQLException
     */
    public MariaDBConnection(final String url, final String user, final String password) throws SQLException {
    	try {
    		getConnection(url, user, password);
    	} catch (SQLException sqlException) {
    		throw sqlException;
    	};
    }
    
    /**
     * Constructor of MariaDBConnection. close() function must be called.
     * @param String helmReleaseName - helm release name
     * @throws Exception 
     */
    public MariaDBConnection(final String namespace, final String helmReleaseName) throws Exception {
    	try {
    		String ipAndPort = K8SUtil.getClusterIpAndPort(namespace, helmReleaseName);
    		String url = "jdbc:mariadb://" + ipAndPort;
    		
    		// TODO: get user, password from DB
    		String user = "root";
    		String password = "qwe123";
    		
    		getConnection(url, user, password);
    	} catch (FileNotFoundException fileNotFoundException) {
    		logger.error("cannot get cluster ip and port", fileNotFoundException);
    		throw fileNotFoundException;
    	} catch (SQLException sqlException) {
    		throw sqlException;
    	};
    }

    /**
     * Please call this function after using.
     */
    public void close() {
		try {
			if (statement != null) {
				statement.close();
			}
        
			if (connection != null) {
				connection.close();
			}
		} catch (SQLException ex) {
			logger.error("Exception in closing:", ex);
		}
    }

	private Connection getConnection(final String url, final String user, final String password) throws SQLException {
		try {
			Class.forName("org.mariadb.jdbc.Driver");
			String jdbcUrl = url + "?user=" + user + "&password=" + password;
			logger.debug("jdbcUrl: {}", jdbcUrl);
			DriverManager.setLoginTimeout(30);
			connection = DriverManager.getConnection(jdbcUrl);
		} catch (SQLException sqlException) {
			throw sqlException;
		} catch (Exception e) {
			logger.error("Exception: ", e);
			close();
			return null;
		}
		
		return this.connection;
	}
	
	public boolean isClosed() {
		if (this.connection != null) {
			try {
				return this.connection.isClosed();
			} catch (SQLException e) {
				logger.error("SQLException.", e);
				return true;
			}
		}
		
		return true;
	}
	
	public static MariaDBConnection getRootMariaDBConnection(final String namespace, final String serviceName) throws Exception {
		return getRootMariaDBConnection(namespace, serviceName, null);
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @param database
	 * @return
	 * @throws Exception
	 */
	public static MariaDBConnection getRootMariaDBConnection(final String namespace, final String serviceName,  final String database) throws Exception {
		MariaDBConnection connection = null;
		try {
			String ipAndPort = K8SUtil.getClusterIpAndPort(namespace, serviceName);

			log.debug("serviceName: {}, ClusterIpAndPort: {}", serviceName, ipAndPort);
			
			if(ipAndPort == null) {
				throw new Exception("Service expose 정보를 알 수 없습니다. [" +namespace +" > "+ serviceName+ "]");
			}

			String url = "jdbc:mariadb://" + ipAndPort;
			
			if(database != null && database.trim().length() > 0) {
				url += "/"+database;
			}
			
			String user = "root";
			String password = MariaDBAccount.getRootPassword(namespace, serviceName);

			if (password != null && !password.isEmpty()) {
				password = new String(Base64.getDecoder().decode(password));
				password = password.trim();
			}

			connection = new MariaDBConnection(url, user, password);
		} catch (Exception e) {
			throw e;
		}

		return connection;
	}
	
	public Statement getStatement() {
		if (this.statement == null) {
			try {
				this.statement = this.connection.createStatement();
			} catch (SQLException e) {
				logger.error("cannot create statement.", e);
			}
		}
		
		return this.statement;
	}
			
	/**
	 * 
	 * An example about MariaDBConfiguration class.
	 * 
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	
	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		// TODO:
	}
}
