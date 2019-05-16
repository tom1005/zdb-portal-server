package com.zdb.core.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class SlaveStatus {

	private String namespace;
	
	private String releaseName;
	
	@Id
	@Column(name = "podName")
	private String podName;
	
	@Column(name = "READ_MASTER_LOG_POS")
	String readMasterLogPos;
	
	@Column(name = "EXEC_MASTER_LOG_POS")
	String execMasterLogPos;
	
	@Column(name = "SLAVE_IO_RUNNING")
	String slaveIORunning;
	
	@Column(name = "SLAVE_SQL_RUNNING")
	String slaveSQLRunning;
	
	@Column(name = "LAST_ERRNO")
	String lastErrno;
	
	@Column(name = "LAST_ERROR")
	String lastError;
	
	@Column(name = "LAST_IO_ERRNO")
	String lastIOErrno;
	
	@Column(name = "LAST_IO_ERROR")
	String lastIOError;
	
	@Column(name = "SECONDS_BEHIND_MASTER")
	String secondsBehindMaster;
	
	@Column(name = "STATUS")
	String status;

	@Column(name = "MESSAGE")
	String message;
	
	@Column(name = "updateTime")
	Date updateTime;
}
