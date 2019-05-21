package com.zdb.core.domain;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ReleaseMetaDataPK implements Serializable {

	private static final long serialVersionUID = -6602555562070654659L;

	@Id
	@Column(name = "releasename")
	String releaseName;

	@Id
	@Column(name = "createTime")
	Date createTime;

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (!(o instanceof ReleaseMetaDataPK)) {
			return false;
		}

		ReleaseMetaDataPK other = (ReleaseMetaDataPK) o;

		return this.releaseName.equals(other.releaseName) && this.createTime.compareTo(other.createTime) == 0;
	}

	@Override
	public int hashCode() {
		return this.releaseName.hashCode() ^ this.createTime.hashCode();
	}

}
