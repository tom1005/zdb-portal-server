package com.zdb.core.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZDBMariaDBConfig
 * - https://myshare.skcc.com/display/SKMONITOR/BSP+MariaDB+Service
 * 
 * @author nojinho@bluedigm.com
 *
 */
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ZDBMariaDBConfig {

	@Id
	@GeneratedValue
	private Long id;
	
	private Date date;
	private String releaseName;
	private String configMapName;
	
	@Lob
	@Column(length=1000000, name = "value")
	private String value;

}
