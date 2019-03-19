package com.zdb.core.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class JobParameter {
	String txId;
	String namespace;
	String podName;
	String serviceType;
	String serviceName;
	String accessMode = "ReadWriteOnce";
	String billingType = "hourly";
	String size;
	String storageClass;
	String statefulsetName;
	String sourcePvc;
	String targetPvc;
	String cpu;
	String memory;
	int toggle;
}
