package com.zdb.core.domain;

public class NamespaceResource {
	private String namespace;
	private ResourceQuota hard;
	private ResourceQuota used;
	private LimitRange limitRange;

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public ResourceQuota getHard() {
		return hard;
	}

	public void setHard(ResourceQuota hard) {
		this.hard = hard;
	}

	public ResourceQuota getUsed() {
		return used;
	}

	public void setUsed(ResourceQuota used) {
		this.used = used;
	}

	public LimitRange getLimitRange() {
		return limitRange;
	}

	public void setLimitRange(LimitRange limitRange) {
		this.limitRange = limitRange;
	}

}
