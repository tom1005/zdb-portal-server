package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class PersistenceSpec {

	private String podType;
	
	private String pvcName;
	
	private boolean enabled;
	
	private String path;

	private String subPath;

	private String storageClass;
	
	private String accessMode;
	
	private String size;

	private String billingType;
	
	private String annotations;
}
