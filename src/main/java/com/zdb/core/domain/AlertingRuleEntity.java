package com.zdb.core.domain;

import lombok.Data;

@Data
public class AlertingRuleEntity {

	private String alert;
	private String expr;
	private String type;
	private String namespace;
	private String channel;
	private String serviceName;
	private String serviceType;
	private String duration;
	private String severity;
	private String priority;
	private String condition;
	private String value;
	private String value2; 
}
