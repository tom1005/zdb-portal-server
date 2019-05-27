package com.zdb.core.domain;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@IdClass(value=DiskUsagPK.class)
public class DiskUsage implements Serializable {

	private static final long serialVersionUID = 8114984462565484772L;

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
	 * 경로
	 */
	@Id
	private String path;

	/**
	 * 사용률(%)
	 */
	private String useRate;

	@Column(name = "updateTime")
	Date updateTime;
}
