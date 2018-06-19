package com.zdb.core.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * ZDBRedisConfig
 * 
 * @author chanhokim
 *
 */
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ZDBRedisConfig {
	
	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	@Column(name = "id")
	private String id;
	
	private String releaseName;
	
	private String timeout;
	private String tcpKeepalive;
	private String maxmemoryPolicy;
	private String maxmemorySamples;
	private String slowlogLogSlowerThan;
	private String slowlogMaxLen;
	private String notifyKeyspaceEvents;
	private String hashMaxZiplistEntries;
	private String hashMaxZiplistValue;
	private String listMaxZiplistSize;
	private String zsetMaxZiplistEntries;
	private String zsetMaxZiplistValue;
	private String save;
}