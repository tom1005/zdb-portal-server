package com.zdb.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.zdb.core.domain.ZDBMariaDBConfig;

@Repository
public interface ZDBMariaDBConfigRepository extends JpaRepository<ZDBMariaDBConfig, String> {
	ZDBMariaDBConfig findByReleaseName(final String releaseName);
	
	@Transactional
	void deleteByReleaseName(final String releaseName);
}
