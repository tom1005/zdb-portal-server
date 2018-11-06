package com.zdb.core.util;

import java.util.List;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;

public class PodManager {

	public static boolean podStatus(Pod pod) {
		boolean isSuccess = false;
		
		try {
			PodStatus status = pod.getStatus();
			String name = pod.getMetadata().getName();
			String phase = status.getPhase();
			
			String reason = status.getReason();
			String message = status.getMessage();
			
			boolean isInitialized = false;
			boolean isReady = false;
			boolean isPodScheduled = false;
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				String podConditionMessage = condition.getMessage();
				String podConditionReason = condition.getReason();
				
				if ("Initialized".equals(condition.getType())) {
					isInitialized = Boolean.parseBoolean(condition.getStatus());
				}
				
				if ("Ready".equals(condition.getType())) {
					isReady = Boolean.parseBoolean(condition.getStatus());
				}
				
				if ("PodScheduled".equals(condition.getType())) {
					isPodScheduled = Boolean.parseBoolean(condition.getStatus());
				}
			}
			
			List<ContainerStatus> containerStatuses = status.getContainerStatuses();
			
			boolean isContainerReady = false;
			for (ContainerStatus containerStatus : containerStatuses) {
				Boolean ready = containerStatus.getReady();
				if (!ready.booleanValue()) {
					isContainerReady = false;
					break;
				} else {
					isContainerReady = true;
				}
			}
			
			if (isInitialized && isReady && isPodScheduled && isContainerReady) {
				isSuccess = true;
			} else {
				//log.info("Name : {}, Initialized : {}, Ready : {}, PodScheduled : {}, isContainerReady : {}, reason : {}, message : {}", name, isInitialized, isReady, isPodScheduled, isContainerReady, reason, message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return isSuccess;
	}
	
	public static boolean isInitialized(Pod pod) {
		try {
			PodStatus status = pod.getStatus();
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				if ("Initialized".equals(condition.getType())) {
					return Boolean.parseBoolean(condition.getStatus());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public static boolean isReady(Pod pod) {
		try {
			PodStatus status = pod.getStatus();
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				if ("Ready".equals(condition.getType())) {
					return Boolean.parseBoolean(condition.getStatus());
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public static String getPodLastTransitionTime(Pod pod) {
		try {
			PodStatus status = pod.getStatus();
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				if ("Ready".equals(condition.getType())) {
					
					String lastTransitionTime = condition.getLastTransitionTime();
					lastTransitionTime = lastTransitionTime.replace("T", " ").replace("Z", "");
					//lastTransitionTime = elapsedTime(lastTransitionTime);
					
					return lastTransitionTime;
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return "";
	}
	
	public static boolean isPodScheduled(Pod pod) {
		try {
			PodStatus status = pod.getStatus();
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				if ("PodScheduled".equals(condition.getType())) {
					return Boolean.parseBoolean(condition.getStatus());
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public static boolean isContainerReady(Pod pod) {
		boolean isContainerReady = false;
		try {
			PodStatus status = pod.getStatus();
			
			List<ContainerStatus> containerStatuses = status.getContainerStatuses();
			
			for (ContainerStatus containerStatus : containerStatuses) {
				Boolean ready = containerStatus.getReady();
				if (!ready.booleanValue()) {
					isContainerReady = false;
					break;
				} else {
					isContainerReady = true;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return isContainerReady;
	}

}
