package com.zdb.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.zdb.core.domain.ZDBMariaDBAccount;

@Repository
public interface ZDBMariaDBAccountRepository extends JpaRepository<ZDBMariaDBAccount, String> {
	
	ZDBMariaDBAccount findByReleaseNameAndUserId(final String releaseName, final String userId);
	
	List<ZDBMariaDBAccount> findAllByReleaseName(final String releaseName);
	
	@Transactional
	void deleteByReleaseNameAndUserId(final String releaseName, final String userId);
	
	@Transactional
	void deleteByReleaseName(final String releaseName);
}
