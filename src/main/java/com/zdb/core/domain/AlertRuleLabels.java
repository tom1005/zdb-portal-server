package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class AlertRuleLabels {

	private String channel;
	private String severity;
	private String product;
	private String priority;
	private String namespace;
	private String serviceName;
}
