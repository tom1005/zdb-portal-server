package com.zdb.core.domain;

import java.util.List;

import lombok.Data;

@Data
public class PrometheusEntity {
	private List<PrometheusGroups> groups;
}
