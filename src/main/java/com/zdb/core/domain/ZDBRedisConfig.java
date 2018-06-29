package com.zdb.core.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.annotations.GenericGenerator;

import com.google.gson.annotations.SerializedName;

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
	@SerializedName("tcp-keepalive")
	private String tcpKeepalive;
	@SerializedName("maxmemory-policy")
	private String maxmemoryPolicy;
	@SerializedName("maxmemory-samples")
	private String maxmemorySamples;
	@SerializedName("slowlog-log-slower-than")
	private String slowlogLogSlowerThan;
	@SerializedName("slowlog-max-len")
	private String slowlogMaxLen;
	@SerializedName("notify-keyspace-events")
	private String notifyKeyspaceEvents;
	@SerializedName("hash-max-ziplist-entries")
	private String hashMaxZiplistEntries;
	@SerializedName("hash-max-ziplist-value")
	private String hashMaxZiplistValue;
	@SerializedName("list-max-ziplist-size")
	private String listMaxZiplistSize;
	@SerializedName("zset-max-ziplist-entries")
	private String zsetMaxZiplistEntries;
	@SerializedName("zset-max-ziplist-value")
	private String zsetMaxZiplistValue;
	private String save;
}