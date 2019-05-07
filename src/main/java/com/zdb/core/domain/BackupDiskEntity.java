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
public class BackupDiskEntity {
	
	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	private String backupDiskId;

	private String namespace;
	private String serviceName;
	private String serviceType;
	
	private int diskSize;
	private String pvcName;
	private String pvName;
	private String mountPath = "/backup";
	
	private String deleteYn;
	private String status;
	
	private Date createdDatetime;
	private Date deleteDatetime;
	
	
}
