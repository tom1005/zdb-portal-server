package com.zdb.core.vo;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "cpu", "memory" })
public class Usage {

	@JsonProperty("cpu")
	private String cpu;
	@JsonProperty("memory")
	private String memory;
	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	@JsonProperty("cpu")
	public String getCpu() {
		return cpu;
	}

	@JsonProperty("cpu")
	public void setCpu(String cpu) {
		this.cpu = cpu;
	}

	@JsonProperty("memory")
	public String getMemory() {
		return memory;
	}

	@JsonProperty("memory")
	public void setMemory(String memory) {
		this.memory = memory;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

}