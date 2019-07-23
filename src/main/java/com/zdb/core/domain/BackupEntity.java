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
public class BackupEntity {

	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	private String backupId;
	
	private String scheduleId;
	private String scheduleYn;
	private String namespace;
	private String serviceName;
	private String serviceType;
	private Date acceptedDatetime;
	private Date startDatetime;
	private Date createdDatetime;
	private Date archivedDatetime;
	private Date completeDatetime;
	private Date deleteDatetime;
	private String status;
	private String reason;
	private String filePath;
	private String fileName;
	private long fileSize;
//	private String archiveName;
	private long archiveFileSize;
	private String archiveFileInfo;
	private String checkSum;
	private String endpointUrl;
	private String bucketName;
	private String type;
	private long toLsn = 0l;
	private long fromLsn = 0l;
	private String fromBackupId = "";
	private String ondisk;
	private String throttleYn;
	
}
