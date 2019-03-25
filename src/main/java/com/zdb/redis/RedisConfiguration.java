package com.zdb.redis;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.domain.ZDBRedisConfig;
import com.zdb.core.repository.ZDBRedisConfigRepository;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

/**
 * 
 * Class to control Redis Configuration.
 * 
 * @author chanhokim
 *
 */
public class RedisConfiguration {
	private static final Logger logger = LoggerFactory.getLogger(RedisConfiguration.class);
	
	/**
	 * Function to get Redis configurations.
	 * 
	 * @param jedis
	 * @param configurationName
	 * @return
	 * @throws JedisException
	 */
	public static String getConfig(final Jedis jedis, final String configurationName) throws JedisException {
		String value = null;
		try {
			value = jedis.configGet(configurationName).get(1);
		} catch (JedisException e) {
			logger.error("Exception: ", e);
			throw e;
		}
		return value;
	}
	
	public static List<String> getAllConfig(final Jedis jedis) throws JedisException {
		List<String> value = null;
		try {
			value = jedis.configGet("*");
		} catch (JedisException e) {
			logger.error("Exception: ", e);
			throw e;
		}
		return value;
	}
	
	/**
	 * Function to set Redis Configuration. (to Service & DB)
	 * 
	 * @param repo
	 * @param namespace
	 * @param releaseName
	 * @param zdbRedisConfig
	 * @return
	 * @throws Exception
	 * @throws JedisException
	 */
	public static void setConfig(final ZDBRedisConfigRepository repo, final Jedis jedis, final String namespace, final String releaseName, Map<String, String> newConfigMap) throws Exception, JedisException {
		// Set Configuration to Service
		setConfig(jedis, "timeout"					, newConfigMap.get("timeout"));
		setConfig(jedis, "tcp-keepalive"			, newConfigMap.get("tcp-keepalive"));
		setConfig(jedis, "maxmemory-policy"			, newConfigMap.get("maxmemory-policy"));
		setConfig(jedis, "maxmemory-samples"		, newConfigMap.get("maxmemory-samples"));
		setConfig(jedis, "slowlog-log-slower-than"	, newConfigMap.get("slowlog-log-slower-than"));
		setConfig(jedis, "slowlog-max-len"			, newConfigMap.get("slowlog-max-len"));
		setConfig(jedis, "hash-max-ziplist-entries"	, newConfigMap.get("hash-max-ziplist-entries"));
		setConfig(jedis, "hash-max-ziplist-value"	, newConfigMap.get("hash-max-ziplist-value"));
		setConfig(jedis, "list-max-ziplist-size"	, newConfigMap.get("list-max-ziplist-size"));
		setConfig(jedis, "zset-max-ziplist-entries"	, newConfigMap.get("zset-max-ziplist-entries"));
		setConfig(jedis, "zset-max-ziplist-value"	, newConfigMap.get("zset-max-ziplist-value"));
		if (!newConfigMap.get("notify-keyspace-events").equals("")) {
			setConfig(jedis, "notify-keyspace-events"	, newConfigMap.get("notify-keyspace-events"));
		}
		
		if ("true".equals(newConfigMap.get("save"))) {
			setConfig(jedis, "save", "900 1 300 10 60 10000");
		} else {
			setConfig(jedis, "save", "");
		}
	}
	
	public static void setConfig(final Jedis jedis, final String key, final String value) throws Exception, JedisException {
		try {
			if(key == null) {
				logger.error("Key is null");
				return;
			}
			jedis.configSet(key, value);
			String query = String.format("CONFIG SET " + key + " " + value);
			logger.debug("Query:", query);
		} catch (Exception e) {
			logger.error("Exception: ", e);
			throw e;
		}
	}
}