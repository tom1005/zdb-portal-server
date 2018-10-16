package com.zdb.core.domain;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZDBPersistenceEntity
 * 
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ZDBPersistenceEntity {

	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	@Column(name = "id")
	private String id;
	
	private String serviceType;

	private String serviceName;

	private String namespace;
	
	private String podName;

	private PersistenceSpec persistenceSpec;
	
}
