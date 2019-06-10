package com.zdb.core.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * MariadbVariableId
 *
 */
@Data
public class MariaDBVariablePk implements Serializable {
	private static final long serialVersionUID = -14175126038210475L;
	
	private String category;
	private String name;
	
}
