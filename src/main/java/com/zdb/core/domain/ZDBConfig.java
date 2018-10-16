package com.zdb.core.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ZDBConfig {
	
	@Id
	@GeneratedValue
	private Long id;
	
	private String namespace;
	
	private String dbType;
	
	private String config;
	
	private String configName;
	
	private String value;
}