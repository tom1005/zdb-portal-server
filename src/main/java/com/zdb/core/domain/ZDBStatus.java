package com.zdb.core.domain;

public enum ZDBStatus {

	GREEN(0), YELLOW(1), RED(-1), GRAY(9);
	
	private int code;

	ZDBStatus(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}
}
