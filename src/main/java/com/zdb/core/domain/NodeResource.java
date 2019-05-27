package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class NodeResource {
	private String nodeName;
	private String nodeType;
	private String nodeRoles;
	private String status;
	private Double cpuRequestsPercentage;
	private Double memoryRequestsPercentage;
	private Double cpuLimitsPercentage;
	private Double memoryLimitsPercentage;
	private Long   creationTime;
	private String allocatableCpu;
	private String allocatableMemory;
	private String cpuRequests;
	private String memoryRequests;
	private String cpuLimits;
	private String memoryLimits;
}
