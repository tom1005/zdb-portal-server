package com.zdb.core.domain;

import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mycnf
 *
 */
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Mycnf {

	@Id
	private String name;
	private String value;
	private String label;
	private String valueRange;
	private String dataType;
	private boolean dynamic;
	private String description;
	
}
