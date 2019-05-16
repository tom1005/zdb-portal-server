package com.zdb.core.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZDBEntity
 * 
 * @author 07517
 *
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ServiceOverview {

	private String version;
	
	private String serviceType;

	private String serviceName;

	private String namespace;

	private boolean clusterEnabled;
	
	private int clusterSlaveCount;
	
	private String deploymentStatus;
	
	private String elapsedTime;
	
	private ZDBStatus status;
	
	private String statusMessage;
	
	private String reason;

	private String purpose;
	
	private boolean publicEnabled;
	
	private boolean backupEnabled;
	
	private String failoverEnabled;
	
	private SlaveReplicationStatus slaveStatus;
	
	private String serviceFailOverStatus;
	
	private List<PersistentVolumeClaim> persistentVolumeClaims = new ArrayList<>();
	
	private List<Deployment> deployments = new ArrayList<>();
	
	private List<Pod> pods = new ArrayList<>();
	
	private List<Service> services = new ArrayList<>();
	
	private List<ConfigMap> configMaps = new ArrayList<>();
	
	private List<StatefulSet> statefulSets = new ArrayList<>();
	
	private List<ReplicaSet> replicaSets = new ArrayList<>();
	
	private List<Secret> secrets = new ArrayList<>();
	
	private List<Tag> tags = new ArrayList<>();
	
	private ResourceSpec resourceSpec;
	
	private PersistenceSpec persistenceSpec;
	
	private Map<String, ResourceSpec> resourceSpecOfPodMap = new HashMap<>();
	
	private Map<String, PersistenceSpec> persistenceSpecOfPodMap = new HashMap<>();
	
	private Map<String, DiskUsage> diskUsageOfPodMap = new HashMap<>();

	private Map<String, Object> cpuUsageOfPodMap = new HashMap<>();
	
	private Map<String, Object> memoryUsageOfPodMap = new HashMap<>();
	
	/**
	 * @return
	 */
	public String getServiceType() {
		return serviceType.toLowerCase();
	}
}
