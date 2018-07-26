package com.zdb.core.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RequestEvent {
	public static final String CREATE = "CREATE";
	public static final String UPDATE = "UPDATE";
	public static final String DELETE = "DELETE";
	public static final String READ = "READ";
	public static final String RESTART = "RESTART";
	public static final String RESTORE = "RESTORE";
	
	private String operation;

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
	
	@Column(name = "startTime")
	private Date startTime;
	
	@Column(name = "endTime")
	private Date endTime;
	
	@Column(name = "updateTime")
	private Date updateTime;
	
	/**
	 * IResult.OK, IResult.RUNNING, IResult.WARNING, IResult.ERROR
	 */
	private int status = IResult.INIT;
	
	@Lob
	@Column(length=1000000, name = "resultMessage")
	private String resultMessage;
	
	private String statusMessage;

	
}
