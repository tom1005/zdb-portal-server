package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ResourceSpec {

	String resourceType;
	
	String cpu;
	
	String memory;	
	
	String workerPool;
}
