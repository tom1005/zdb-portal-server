package com.zdb.core.domain;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZDBEntity
 * 
 * @author 07517
 *
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ZDBEntity {

	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	@Column(name = "id")
	private String id;
	
	private String version;

	private String requestUserId;
	
	private String serviceType;

	private String serviceName;

	private String namespace;

	private boolean clusterEnabled;
	
	private int clusterSlaveCount;
	
	private boolean metricEnabled;
	
	private boolean backupEnabled;
	
	private PodSpec[] podSpec;
	
	private ServiceSpec[] serviceSpec;

	private String persistenceExistingClaim;
		
	private PersistenceSpec[] persistenceSpec;
	
	private boolean usePassword;
	
	private String existingSecret;

	private String password;
	
	private MariaDBConfig mariaDBConfig;
	
	private RedisConfig[] redisConfig; 
	
	private String purpose;
	
	private String deployType;

	
	/**
	 * @return
	 */
	public String getServiceType() {
		return serviceType.toLowerCase();
	}
}
