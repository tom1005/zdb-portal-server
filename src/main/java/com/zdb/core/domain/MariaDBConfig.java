package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZDBEntity
 * 
 * @author 06919
 *
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class MariaDBConfig{

	private String mariadbUser;
	
	private String mariadbPassword;
	
	private String mariadbDatabase;
	
	private String config;

}
