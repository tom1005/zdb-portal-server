package com.zdb.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ResourceQuota {
	private Integer cpuLimits;
	private CPUUnit cpuLimitsUnit;
	private Integer cpuRequests;
	private CPUUnit cpuRequestsUnit;
	private Integer memoryLimits;
	private MemoryUnit memoryLimitsUnit;
	private Integer memoryRequests;
	private MemoryUnit memoryRequestsUnit;

	private Integer configmaps;
	private Integer persistentvolumeclaims;
	private Integer pods;
	private Integer replicationcontrollers;
	private Integer resourcequotas;
	private Integer services;
	private Integer servicesLoadbalancers;
	private Integer servicesNodeports;
	private Integer secrets;

	@JsonIgnore
	public String getCpuLimitsFormat() {
		if (cpuLimits == null) {
			return null;
		}

		if (cpuLimitsUnit == null) {
			return null;
		}

		return String.valueOf(cpuLimits) + ((cpuLimitsUnit == CPUUnit.Core) ? "" : "m");
	}

	@JsonIgnore
	public String getCpuRequestsFormat() {
		if (cpuRequests == null) {
			return null;
		}

		if (cpuRequestsUnit == null) {
			return null;
		}

		return String.valueOf(cpuRequests) + ((cpuRequestsUnit == CPUUnit.Core) ? "" : "m");
	}

	@JsonIgnore
	public String getMemoryLimitsFormat() {
		if (memoryLimits == null) {
			return null;
		}

		if (memoryLimitsUnit == null) {
			return null;
		}

		return String.valueOf(memoryLimits) + memoryLimitsUnit.name();
	}

	@JsonIgnore
	public String getMemoryRequestsFormat() {
		if (memoryRequests == null) {
			return null;
		}

		if (memoryRequestsUnit == null) {
			return null;
		}

		return String.valueOf(memoryRequests) + memoryRequestsUnit.name();
	}

	public Integer getCpuLimits() {
		return cpuLimits;
	}

	public void setCpuLimits(Integer cpuLimits) {
		this.cpuLimits = cpuLimits;
	}

	public CPUUnit getCpuLimitsUnit() {
		return cpuLimitsUnit;
	}

	public void setCpuLimitsUnit(CPUUnit cpuLimitsUnit) {
		this.cpuLimitsUnit = cpuLimitsUnit;
	}

	public Integer getCpuRequests() {
		return cpuRequests;
	}

	public void setCpuRequests(Integer cpuRequests) {
		this.cpuRequests = cpuRequests;
	}

	public CPUUnit getCpuRequestsUnit() {
		return cpuRequestsUnit;
	}

	public void setCpuRequestsUnit(CPUUnit cpuRequestsUnit) {
		this.cpuRequestsUnit = cpuRequestsUnit;
	}

	public Integer getMemoryLimits() {
		return memoryLimits;
	}

	public void setMemoryLimits(Integer memoryLimits) {
		this.memoryLimits = memoryLimits;
	}

	public MemoryUnit getMemoryLimitsUnit() {
		return memoryLimitsUnit;
	}

	public void setMemoryLimitsUnit(MemoryUnit memoryLimitsUnit) {
		this.memoryLimitsUnit = memoryLimitsUnit;
	}

	public Integer getMemoryRequests() {
		return memoryRequests;
	}

	public void setMemoryRequests(Integer memoryRequests) {
		this.memoryRequests = memoryRequests;
	}

	public MemoryUnit getMemoryRequestsUnit() {
		return memoryRequestsUnit;
	}

	public void setMemoryRequestsUnit(MemoryUnit memoryRequestsUnit) {
		this.memoryRequestsUnit = memoryRequestsUnit;
	}

	public Integer getConfigmaps() {
		return configmaps;
	}

	public void setConfigmaps(Integer configmaps) {
		this.configmaps = configmaps;
	}

	public Integer getPersistentvolumeclaims() {
		return persistentvolumeclaims;
	}

	public void setPersistentvolumeclaims(Integer persistentvolumeclaims) {
		this.persistentvolumeclaims = persistentvolumeclaims;
	}

	public Integer getPods() {
		return pods;
	}

	public void setPods(Integer pods) {
		this.pods = pods;
	}

	public Integer getReplicationcontrollers() {
		return replicationcontrollers;
	}

	public void setReplicationcontrollers(Integer replicationcontrollers) {
		this.replicationcontrollers = replicationcontrollers;
	}

	public Integer getResourcequotas() {
		return resourcequotas;
	}

	public void setResourcequotas(Integer resourcequotas) {
		this.resourcequotas = resourcequotas;
	}

	public Integer getServices() {
		return services;
	}

	public void setServices(Integer services) {
		this.services = services;
	}

	public Integer getServicesLoadbalancers() {
		return servicesLoadbalancers;
	}

	public void setServicesLoadbalancers(Integer servicesLoadbalancers) {
		this.servicesLoadbalancers = servicesLoadbalancers;
	}

	public Integer getServicesNodeports() {
		return servicesNodeports;
	}

	public void setServicesNodeports(Integer servicesNodeports) {
		this.servicesNodeports = servicesNodeports;
	}

	public Integer getSecrets() {
		return secrets;
	}

	public void setSecrets(Integer secrets) {
		this.secrets = secrets;
	}

	@JsonIgnore
	public boolean isEmpty() {
		if (cpuLimits != null)
			return false;
		if (cpuRequests != null)
			return false;
		if (memoryLimits != null)
			return false;
		if (memoryRequests != null)
			return false;
		if (configmaps != null)
			return false;
		if (persistentvolumeclaims != null)
			return false;
		if (pods != null)
			return false;
		if (replicationcontrollers != null)
			return false;
		if (resourcequotas != null)
			return false;
		if (services != null)
			return false;
		if (servicesLoadbalancers != null)
			return false;
		if (servicesNodeports != null)
			return false;
		if (secrets != null)
			return false;

		return true;
	}

}
