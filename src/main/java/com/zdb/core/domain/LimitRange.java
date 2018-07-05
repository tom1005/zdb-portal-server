package com.zdb.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class LimitRange {
	private Integer cpuLimits;
	private CPUUnit cpuLimitsUnit;
	private Integer cpuRequests;
	private CPUUnit cpuRequestsUnit;
	private Integer memoryLimits;
	private MemoryUnit memoryLimitsUnit;
	private Integer memoryRequests;
	private MemoryUnit memoryRequestsUnit;

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

		return true;
	}

}
