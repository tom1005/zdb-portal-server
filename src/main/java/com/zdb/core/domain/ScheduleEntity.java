package com.zdb.core.domain;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ScheduleEntity {

	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	private String scheduleId;	
	private String serviceName;
	private String serviceType;
	private String namespace;
	private String startTime;
	private int storePeriod;
	private String useYn;
	private Date registerDate;
	private String deleteYn = "N";
	private Date deleteDate;
	private String incrementYn = "N";
	private int incrementPeriod = 12;
	private long currentLsn = 0l;
	private String currentBackupId;
	private String scheduleType = "DAILY";
	private int scheduleDay = 0;
	private String notiYn = "N";
	private String throttleYn = "N";
	
}
