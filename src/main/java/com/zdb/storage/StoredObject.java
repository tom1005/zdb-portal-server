package com.zdb.storage;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class StoredObject {
	private String key;
	private String bucketName;
	private String endPointUrl;
	private long size;
}
