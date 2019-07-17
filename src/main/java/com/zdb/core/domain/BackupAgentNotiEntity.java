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
public class BackupAgentNotiEntity {

	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	private String backupAgentNotiId;

	private String actionId;
	private String actionType;
	private String namespace;
	private String serviceName;
	private String serviceType;
	private Date acceptedDatetime;
	private Date startDatetime;
	private Date errorDatetime;
	private Date notiDatetime;
	private String notiFlag = "N";
	
}