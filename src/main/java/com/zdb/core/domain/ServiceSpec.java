package com.zdb.core.domain;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ServiceSpec {

	private String podType;
	
	private String serviceType;
	
	private int servicePort;
	
	private String loadBalancerIP;
	
	private String loadBalancerType;
	
	private Map<String, String> annotations;
}
