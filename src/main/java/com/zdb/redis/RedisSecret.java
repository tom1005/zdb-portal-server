package com.zdb.redis;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.domain.ZDBMariaDBAccount;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Secret;
import lombok.Getter;

public class RedisSecret {
	private static final Logger logger = LoggerFactory.getLogger(RedisSecret.class);
	
	@Getter
	private String id;
	@Getter
	private String password;

	public RedisSecret(final String releaseName, final String id) {
		this.id = id;
	}

	public RedisSecret(final String id, final String password, final ZDBMariaDBAccount account) {
		this.id = id;
		this.password = password;
	}

	public RedisSecret(final ZDBMariaDBAccount account) {
		this.id = account.getUserId();
		this.password = account.getUserPassword();
	}
	
	public static String getSecret(final String namespace, final String releaseName) {
		Secret secret = null;
		try {
			secret = K8SUtil.getSecret(namespace, releaseName);
			if (secret != null) {
				Map<String, String> data = secret.getData();
				if (!data.isEmpty()) {
					if (logger.isDebugEnabled()) {
						logger.debug("Redis Secret: {}", data.get("redis-password"));
					}
					return data.get("redis-password");
				}
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
			return null;
		}

		return null;
	}
}