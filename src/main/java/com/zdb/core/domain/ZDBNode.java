package com.zdb.core.domain;

import lombok.Data;

@Data
public class ZDBNode {
	private String nodeName;
	private String nodeType;
	private String nodeRoles;
	private String status;
	private String allocatableCpu;
	private String allocatableCpuString;
	private String allocatableMemory;
	private String allocatableMemoryString;
	private String cpuRequests;
	private String cpuRequestsString;
	private String cpuRequestsPercentage;
	private String memoryRequests;
	private String memoryRequestsString;
	private String memoryRequestsPercentage;
	private String cpuLimits;
	private String cpuLimitsString;
	private String cpuLimitsPercentage;
	private String memoryLimits;
	private String memoryLimitsString;
	private String memoryLimitsPercentage;
	private String creationTime;
	
	private String workerPool;
	private String memory;
	private String cpu;
	private String machineType;
}
