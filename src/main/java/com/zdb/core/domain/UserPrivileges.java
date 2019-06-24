package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrivileges {

	private String grantee; 
	private String tableCatalog; 
	private String tableSchema; 
	private String privilegeType; 
	private String isGrantable;
}
