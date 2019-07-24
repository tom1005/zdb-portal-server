package com.zdb.core.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class StorageUsagPK implements Serializable {

	private static final long serialVersionUID = 8114984462565484772L;

	@Id
	@Column(name = "podName")
	private String podName;

	@Id
	private String path;

	@Override
	public boolean equals(Object o) {

		if (o == this) {

			return true;

		}

		if (!(o instanceof StorageUsagPK)) {

			return false;

		}

		StorageUsagPK other = (StorageUsagPK) o;

		return this.podName.equals(other.podName)

				&& this.path.equals(other.path);

	}

	@Override
	public int hashCode() {

		return this.podName.hashCode()

				^ this.path.hashCode();

	}

}
