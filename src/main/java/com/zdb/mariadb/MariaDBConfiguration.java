package com.zdb.mariadb;

import java.io.FileNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.domain.ZDBMariaDBConfig;
import com.zdb.core.repository.ZDBMariaDBAccountRepository;
import com.zdb.core.repository.ZDBMariaDBConfigRepository;

/**
 * 
 * A class to control mariadb variables
 * 
 * @author nojinho@bluedigm.com
 *
 */
public class MariaDBConfiguration {
	private static final Logger logger = LoggerFactory.getLogger(MariaDBConfiguration.class);
	
	public static String DEFAULT_EVENT_SCHEDULER = "OFF";
	public static String DEFAULT_GROUP_CONCAT_MAX_LEN = "1024";
	public static String DEFAULT_MAX_CONNECTIONS = "151";
	public static String DEFAULT_WAIT_TIMEOUT = "28800";
	
	MariaDBConnection connection = null;
    Statement statement = null;
    
    @Inject protected ZDBMariaDBAccountRepository configRepository;
    

    /**
     * Constructor of MariaDBConfiguration. MariaDB Connection will be set by this constructor. close() function must be called.
     * @param url MariaDB URL
     * @param user User ID
     * @param password User Password
     * @throws SQLException
     */
    public MariaDBConfiguration(final MariaDBConnection connection) throws SQLException {
    	this.connection = connection;
    	this.statement = connection.getStatement();
    }
    
    /**
     * Constructor of MariaDBConfiguration. MariaDB Connection will be set by this constructor. close() function must be called.
     * @param String helmReleaseName - helm release name
     * @throws Exception 
     */
    public MariaDBConfiguration(final String namespace, final String helmReleaseName) throws Exception {
    	try {
    		this.connection = MariaDBConnection.getRootMariaDBConnection(namespace, helmReleaseName);
    	} catch (FileNotFoundException fileNotFoundException) {
    		logger.error("cannot get cluster ip and port", fileNotFoundException);
    		throw fileNotFoundException;
    	} catch (SQLException sqlException) {
    		throw sqlException;
    	};
    }
    
//    public static ZDBMariaDBConfig craeteDefaultConfig(final ZDBMariaDBConfigRepository repo, final String namespace, final String releaseName) {
//    	ZDBMariaDBConfig config = new ZDBMariaDBConfig(null, releaseName, DEFAULT_EVENT_SCHEDULER, DEFAULT_GROUP_CONCAT_MAX_LEN, DEFAULT_MAX_CONNECTIONS, DEFAULT_WAIT_TIMEOUT);
//    	repo.save(config);
//    	
//    	return config;
//    }

    /**
     * Please call this function after using.
     */
    public void close() {
    	if (connection != null) {
    		connection.close();
		}
    }

	public void setConfig(final String key, final String value) throws Exception, SQLException {
		try {
			if (statement == null) {
				statement = connection.getStatement();
			}
			
			setConfig(statement, key, value);
		} catch (Exception e) {
			logger.error("Exception: ", e);
			throw e;
		}
	}
	
//	public static ZDBMariaDBConfig setConfig(final ZDBMariaDBConfigRepository repo, final Statement statement, final String namespace, final String releaseName, Map<String, String> newConfigMap) throws Exception, SQLException {
////		static String[] configs = new String[]{"event_scheduler","group_concat_max_len","max_connections","wait_timeout"};		
//		
//		setConfig(statement, "max_connections", newConfigMap.get("maxConnections") == null ? DEFAULT_MAX_CONNECTIONS : newConfigMap.get("maxConnections"));
//		setConfig(statement, "event_scheduler", newConfigMap.get("eventScheduler") == null ? DEFAULT_EVENT_SCHEDULER : newConfigMap.get("eventScheduler"));
//		setConfig(statement, "group_concat_max_len", newConfigMap.get("groupConcatMaxLen") == null ? DEFAULT_GROUP_CONCAT_MAX_LEN : newConfigMap.get("groupConcatMaxLen"));
//		setConfig(statement, "wait_timeout", newConfigMap.get("waitTimeout") == null ? DEFAULT_WAIT_TIMEOUT : newConfigMap.get("waitTimeout"));
//		
//		ZDBMariaDBConfig config = repo.findByReleaseName(releaseName);
//		
//		ZDBMariaDBConfig newConfig = null;
//		if(config == null) {
//			newConfig = new ZDBMariaDBConfig();
//			
//			newConfig.setReleaseName(releaseName);
//
//			newConfig.setMaxConnections(newConfigMap.get("maxConnections") == null ? DEFAULT_MAX_CONNECTIONS : newConfigMap.get("maxConnections"));
//			newConfig.setEventScheduler(newConfigMap.get("eventScheduler") == null ? DEFAULT_EVENT_SCHEDULER : newConfigMap.get("eventScheduler"));
//			newConfig.setGroupConcatMaxLen(newConfigMap.get("groupConcatMaxLen") == null ? DEFAULT_GROUP_CONCAT_MAX_LEN : newConfigMap.get("groupConcatMaxLen"));
//			newConfig.setWaitTimeout(newConfigMap.get("waitTimeout") == null ? DEFAULT_WAIT_TIMEOUT : newConfigMap.get("waitTimeout"));
//			repo.save(newConfig);
//		} else {
//			config.setMaxConnections(newConfigMap.get("maxConnections") == null ? DEFAULT_MAX_CONNECTIONS : newConfigMap.get("maxConnections"));
//			config.setEventScheduler(newConfigMap.get("eventScheduler") == null ? DEFAULT_EVENT_SCHEDULER : newConfigMap.get("eventScheduler"));
//			config.setGroupConcatMaxLen(newConfigMap.get("groupConcatMaxLen") == null ? DEFAULT_GROUP_CONCAT_MAX_LEN : newConfigMap.get("groupConcatMaxLen"));
//			config.setWaitTimeout(newConfigMap.get("waitTimeout") == null ? DEFAULT_WAIT_TIMEOUT : newConfigMap.get("waitTimeout"));
//			
//			repo.save(config);
//			
//			newConfig = config;
//		}
//		
//		return newConfig;
//	}
	
	public static void setConfig(final Statement statement, final String key, final String value) throws Exception, SQLException {
		try {
			if(key == null || value == null || value.length() == 0) {
				logger.error("key : {}, value : {}", key, value);
				return;
			}
			String query = String.format("set global %s = %s;", key, value);
			logger.debug("query: {}", query);

			statement.executeUpdate(query);

		} catch (Exception e) {
			logger.error("Exception: ", e);
			throw e;
		}
	}

	/**
	 * select global variable of MariaDB
	 * @param key - variable name
	 * @return - value string
	 * @throws Exception
	 */
//	public String getConfig(final ZDBMariaDBAccountRepository repo, final String namespace, final String releaseName, final String key) throws SQLException {
		//return MariaDBConfiguration.getConfig(repo, namespace, releaseName, key);
		//return "";
//	}
	
	/**
	 * 
	 * @param repo
	 * @param namespace
	 * @param releaseName
	 * @param key
	 * @param valueFromPod
	 * @return boolean conflict: false
	 */
//	private static boolean compareConfigValue(final ZDBMariaDBConfigRepository repo, final String namespace, final String releaseName, final String key, String valueFromPod) {
//		ZDBMariaDBConfig config = repo.findByReleaseName(releaseName);
//		if (config != null) {
//			if( valueFromPod == null) valueFromPod ="";
//			
//			if (key.equals("max_connections")) {
//				return valueFromPod.equals(config.getMaxConnections());
//			}
//
//			if (key.equals("event_scheduler")) {
//				return valueFromPod.equals(config.getEventScheduler());
//			}
//
//			if (key.equals("group_concat_max_len")) {
//				return valueFromPod.equals(config.getGroupConcatMaxLen());
//			}
//
//			if (key.equals("wait_timeout")) {
//				return valueFromPod.equals(config.getWaitTimeout());
//			}
//		}
//		return false;
//	}
	
	/**
	 * A static function.
	 * select global variable of MariaDB
	 * @param statement
	 * @param namespace
	 * @param releaseName
	 * @param key - variable name
	 * @return - value string
	 * @throws Exception
	 */
//	public static String getConfig(final ZDBMariaDBConfigRepository repo, final Statement statement, final String namespace, final String releaseName, final String key) throws SQLException {
//		ResultSet resultSet = null;
//		String value = null;
//
//		try {
//			String query = "show variables like '" + key + "'";
//			logger.debug("query: {}", query);
//			
//            resultSet = statement.executeQuery(query);
//
//            if (resultSet != null && resultSet.next()) {
//            	logger.debug("key: {}, value: {}", resultSet.getString("Variable_name"), resultSet.getString("Value"));
//            	value = resultSet.getString("value");
//            }
//            
//            if (false == compareConfigValue(repo, namespace, releaseName, key, value)) {
//            	logger.warn("Real value conflicted with the value of DB. Check it! variableName:" + key + ", valueFromPod: " + value);
//            }
//		} catch (SQLException e) {
//			logger.error("Exception: ", e);
//			throw e;
//		} finally {
//			try {
//				if (resultSet != null) {
//					resultSet.close();
//				}
//			} catch (SQLException ex) {
//				logger.error("Exception in closing:", ex);
//				throw ex;
//			}
//		}
//		
//		return value;
//	}
	
	/**
	 * delete an account
	 * @param id
	 */
	public void deleteConfig(final String id) {
		configRepository.delete(id);
	}
	
	public static void deleteConfig(final ZDBMariaDBConfigRepository repo, final String namespace, final String serviceName) {
		logger.debug("delete config. releaseName: {}", serviceName);
		repo.deleteByReleaseName(serviceName);
	}
}
