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
public class DiskUsage {

	private String namespace;
	
	private String releaseName;
	
	@Id
	@Column(name = "podName")
	private String podName;
	
	/**
	 * 사이즈
	 */
	private long size;

	/**
	 * 사용
	 */
	private long used;
	
	/**
	 * 가용
	 */
	private long avail;
	
	/**
	 * 사용률(%)
	 */
	private String useRate;
	
	@Column(name = "updateTime")
	Date updateTime;
}
