package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class BackupResult {
	
	private String backupScope;
	private String fromBackup;
	private String archiveFileSize;
	private String userName;

}	