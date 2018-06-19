package com.zdb.core.domain;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class PodSpec {

	private String podType;
	
	private String podName;
	
	private ResourceSpec[] resourceSpec;
	
	private Map<String, String> annotations;
	
	private Map<String, String> labels;
	
	private boolean livenessProbeEnabled = true;
	
	private int livenessProbeInitialDelaySeconds = 30;
	
	private int livenessProbePeriodSeconds = 30;
	
	private int livenessProbeTimeoutSeconds = 5;
	
	private int livenessProbeSuccessThreshold = 1;
	
	private int livenessProbeFailureThreshold = 5;	
}
