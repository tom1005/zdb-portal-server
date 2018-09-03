package com.zdb.core.domain;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ScheduleInfoEntity {
	
	private String serviceName;
	private String serviceType;
	private String namespace;
	private String startTime;
	private int storePeriod;
	private Date registerDate;
	
	private Date startDatetime;
	private Date completeDatetime;
	private String executionTime;
	private long executionMilsec;
	private String status;
	private long fileSize;
	private long fileSumSize;
	
}	