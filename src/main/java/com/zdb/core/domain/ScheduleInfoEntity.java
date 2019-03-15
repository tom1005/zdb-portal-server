package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ScheduleInfoEntity {
/*	
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
	private long fileSumSize = 0l;
*/	
	private String namespace;
	private String serviceName;
	private String serviceType;
	private String startTime;
	private int storePeriod;

	private long fullFileSize;
	private String fullExecutionTime;
	private long fullArchiveFileSize;

	private long incrFileSize;
	private String incrExecutionTime;
	private long incrArchiveFileSize;

	private long dumpFileSize;
	private String dumpExecutionTime;
	private long dumpArchiveFileSize;
	
}	