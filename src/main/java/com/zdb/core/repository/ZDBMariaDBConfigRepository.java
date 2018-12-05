package com.zdb.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.zdb.core.domain.ZDBMariaDBConfig;

@Repository
public interface ZDBMariaDBConfigRepository extends JpaRepository<ZDBMariaDBConfig, String> {
	List<ZDBMariaDBConfig> findByReleaseName(final String releaseName);
	
	List<ZDBMariaDBConfig> findByConfigMapName(final String configMapName);
	
	@Transactional
	void deleteByReleaseName(final String releaseName);
}
