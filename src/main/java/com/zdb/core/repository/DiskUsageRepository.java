package com.zdb.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.zdb.core.domain.DiskUsage;

/**
 * 
 * 
 * @author 06919
 *
 */
@Repository
public interface DiskUsageRepository extends CrudRepository<DiskUsage, String> {

	@Query(value = "select release_name, sum(size), sum(avail), sum(used), sum(use_rate) from zdb.disk_usage group by release_name", nativeQuery = true)
	List<DiskUsage> findGroupByReleaseName();

	@Query(value = "select release_name, sum(size) as size, sum(avail) as avail, sum(used) as used, sum(use_rate) as use_rate from zdb.disk_usage where release_name=:release_name", nativeQuery = true)
	Object findGroupByReleaseName(@Param("release_name") String release_name);
	
	@Transactional
	@Query(value = "delete from zdb.disk_usage where namespace =:namespace and release_name =:release_name", nativeQuery = true)
	Integer deleteByNamespaceAndReleaseName(@Param("namespace") String namespace, @Param("release_name") String release_name);
}
