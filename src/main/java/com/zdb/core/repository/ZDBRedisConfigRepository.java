package com.zdb.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.zdb.core.domain.ZDBRedisConfig;

@Repository
public interface ZDBRedisConfigRepository extends JpaRepository<ZDBRedisConfig, String> {
	ZDBRedisConfig findByReleaseName(final String releaseName);
	
	@Transactional
	void deleteByReleaseName(final String releaseName);
}