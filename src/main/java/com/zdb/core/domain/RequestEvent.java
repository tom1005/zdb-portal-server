package com.zdb.core.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
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
	public static final String POD_RESTART = "POD-RESTART";
	public static final String RESTORE = "RESTORE";
	public static final String CREATE_DB_USER = "CREATE-DB-USER";
	public static final String UPDATE_DB_USER = "UPDATE-DB-USER";
	public static final String DELETE_DB_USER = "DELETE-DB-USER";
	public static final String CREATE_TAG = "CREATE-TAG";
	public static final String DELETE_TAG = "DELETE-TAG";
	public static final String SCALE_UP = "SCALE-UP";
	public static final String SCALE_OUT = "SCALE-OUT";
	public static final String UPDATE_CONFIG = "UPDATE-CONFIG";
	public static final String MODIFY_PASSWORD = "MODIFY-PASSWORD";
	public static final String SET_BACKUP_SCHEDULE = "SET-SCHEDULE";
	public static final String EXEC_BACKUP = "EXEC-BACKUP";
	public static final String DELETE_BACKUP_DATA = "DELETE-BACKUP-DATA";
	public static final String RESTORE_BACKUP = "RESTORE-BACKUP";
	
	private String operation;

	@Id
	@GeneratedValue
	private Long id;
	
	@Column(name = "txId")
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
	
	/**
	 * IResult.OK, IResult.RUNNING, IResult.WARNING, IResult.ERROR
	 */
	private int status = IResult.INIT;
	
	@Lob
	@Column(length=1000000, name = "resultMessage")
	private String resultMessage;
}
