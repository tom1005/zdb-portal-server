package com.zdb.core.domain;

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
public class ZDBEventParam {
	private String namespace;
	private String kind;
	private String serviceName;
	private String startDate;
	private String endDate;
	private String keyword;
}
