package com.zdb.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.zdb.core.domain.StorageUsage;

/**
 * 
 * 
 * @author 06919
 *
 */
@Repository
public interface StorageUsageRepository extends CrudRepository<StorageUsage, String> {

//	@Query(value = "select release_name, sum(size), sum(avail), sum(used), sum(use_rate) from zdb.storage_usage group by release_name", nativeQuery = true)
//	List<StorageUsage> findGroupByReleaseName();
	
	@Query(value = "select * from zdb.storage_usage where pod_name=:pod_name", nativeQuery = true)
	List<StorageUsage> findByPodName(@Param("pod_name") String pod_name);

//	@Query(value = "select release_name, sum(size) as size, sum(avail) as avail, sum(used) as used, sum(use_rate) as use_rate from zdb.storage_usage where release_name=:release_name", nativeQuery = true)
//	Object findGroupByReleaseName(@Param("release_name") String release_name);
	
	@Transactional
	@Query(value = "delete from zdb.storage_usage where namespace =:namespace and release_name =:release_name", nativeQuery = true)
	Integer deleteByNamespaceAndReleaseName(@Param("namespace") String namespace, @Param("release_name") String release_name);
	
	@Query(value = "select * from zdb.storage_usage where release_name=:release_name", nativeQuery = true)
	List<StorageUsage> findByReleaseName(@Param("release_name") String release_name);
	
	@Transactional
	@Query(value = "delete from zdb.storage_usage where path = '/backup' and namespace =:namespace and release_name =:release_name", nativeQuery = true)
	Integer deleteBackupStorageByNamespaceAndReleaseName(@Param("namespace") String namespace, @Param("release_name") String release_name);
}
