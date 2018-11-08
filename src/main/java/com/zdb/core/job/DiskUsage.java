package com.zdb.core.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class DiskUsage {

	/**
	 * 사이즈
	 */
	private double size;

	/**
	 * 사용
	 */
	private double used;
	
	/**
	 * 가용
	 */
	private double avail;
	
	/**
	 * 사용률(%)
	 */
	private String useRate;
	
	private String path;

}
