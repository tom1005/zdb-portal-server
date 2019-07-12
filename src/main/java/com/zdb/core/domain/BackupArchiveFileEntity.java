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
public class BackupArchiveFileEntity {

	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	private String backupArchiveFileId;
	
	private String backupId;
	private Date createdDatetime = new Date(System.currentTimeMillis());
	private Date deleteDatetime;
	private String fileName;
	private long fileSize = 0l;
	private String deleteYn = "N";
	private String deleteDesc;
	
}
