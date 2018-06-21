package com.zdb.core.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ZDBCode {

	@Id
	@GeneratedValue
	private Long id;
	
	private String codeGroup;
	
	private String name;
	
	private String value;
	
	private String description;
	
	// codegroup	name	 value	    description
	// CODE0001     kind     mariadb    mariadb
	// CODE0001     kind     redis      redis
	// CODE0001     kind     postgresql postgresql
	// CODE0002     mariadb  10.1.33    mariadb-10.1.33
	// CODE0002     mariadb  10.2.14    mariadb-10.2.14
	// CODE0002     redis    4.0.9      redis-4.0.9
}
