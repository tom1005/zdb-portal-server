package com.zdb.core.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZDBMariaDBAccount
 * - https://myshare.skcc.com/display/SKMONITOR/BSP+MariaDB+Service
 * 
 * @author nojinho@bluedigm.com
 *
 */
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ZDBMariaDBAccount {

	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	@Column(name = "id")
	private String id;
	
	private String releaseName;
	
	private String userId;
	private String userPassword;
	
	private String accessIp;
	
	@NotNull
	@Column(name = "g_create")
	private boolean create;
	
	@NotNull
	@Column(name = "g_read")
	private boolean read;
	
	@NotNull
	@Column(name = "g_update")
	private boolean update;
	
	@NotNull
	@Column(name = "g_delete")
	private boolean delete;
	
	@NotNull
	@Column(name = "g_grant")
	private boolean grant;
	
	public boolean equalsPrivileges(ZDBMariaDBAccount account) {
		if (
				this.create != account.create ||
				this.read != account.read ||
				this.update != account.update ||
				this.delete != account.delete ||
				!this.accessIp.equals(account.getAccessIp())
				) {
			return false;
		}
		
		return true;
	}
}
