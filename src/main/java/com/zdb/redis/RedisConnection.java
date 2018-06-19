package com.zdb.redis;

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.util.K8SUtil;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

/**
 * 
 * Class to manage Redis connection.
 * 
 * @author chanhokim
 *
 */
public class RedisConnection {
	private static final Logger logger = LoggerFactory.getLogger(RedisConnection.class);
	
	/**
	 * Function to create RedisConnection.
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static Jedis getRedisConnection(final String namespace, final String serviceName, final String location) throws Exception, JedisException {
		try {
			String redisHost = K8SUtil.getRedisHostIP(namespace, serviceName, location);
			Integer redisPort = K8SUtil.getServicePort(namespace, serviceName);
			if (redisHost != null) {
				String password = RedisSecret.getSecret(namespace, serviceName);
	
				if (password != null && !password.isEmpty()) {
					password = new String(Base64.getDecoder().decode(password));
				}
				logger.info("redisHost: " + redisHost);
				logger.info("redisPort: " + redisPort);
				Jedis jedis = new Jedis(redisHost, redisPort);
				jedis.connect();
				jedis.auth(password);
				
				logger.info("Redis connection succeeded.");
				
				return jedis;
			}

		} catch (JedisException jedisException) {
			throw jedisException;
		}
		
		return null;
	}
}