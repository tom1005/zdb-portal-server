package com.zdb.core.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RequestEvent {
	private String opertaion;

	@Id
	@Column(name = "id")
	private String txId;
	
	private String namespace;

	@Column(name = "serviceName")
	private String serviceName;
	
	@Column(name = "serviceType")
	private String serviceType;
	
	@Column(name = "userId")
	private String userId;
	
	@Column(name = "eventType")
	private String eventType;
	
	@Column(name = "chartName")
	private String chartName;
	
	@Column(name = "chartVersion")
	private String chartVersion;

//	@Column(name = "appVersion")
//	private String appVersion;
	
	@Lob
	@Column(length=1000000, name = "resourceLog")
	private String resourceLog;
	
	@Column(name = "startTime")
	private Date startTime;
	
	@Column(name = "endTime")
	private Date endTime;
	
	@Column(name = "updateTime")
	private Date updateTime;
	
	private String cluster;
	
	@Lob
	@Column(length=1000000, name = "deploymentYaml")
	private String deploymentYaml;
	
	/**
	 * IResult.OK, IResult.RUNNING, IResult.WARNING, IResult.ERROR
	 */
	private int status = IResult.INIT;
	
	@Lob
	@Column(length=1000000, name = "resultMessage")
	private String resultMessage;
	
	private String statusMessage;

}
