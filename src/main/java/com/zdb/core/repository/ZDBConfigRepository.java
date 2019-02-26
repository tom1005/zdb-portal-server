package com.zdb.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.zdb.core.domain.ZDBConfig;

@Repository
public interface ZDBConfigRepository extends CrudRepository<ZDBConfig, String> {

	@Query(value = "SELECT * FROM zdb.zdbconfig WHERE namespace=:namespace", nativeQuery = true)
	List<ZDBConfig> findByNamespace(@Param("namespace") String namespace);

	@Query(value = "SELECT * FROM zdb.zdbconfig WHERE namespace=:namespace and config=:config", nativeQuery = true)
	List<ZDBConfig> findByNamespaceAndConfig(@Param("namespace") String namespace, @Param("config") String config);
	
	@Modifying
	@Query(value = "UPDATE zdb.zdbconfig SET value=:value WHERE namespace=:namespace AND config=:config", nativeQuery = true)
	List<ZDBConfig> updateZDBConfig(@Param("namespace") String namespace, @Param("config") String config, @Param("value") String value);
	
	@Transactional
	@Modifying
	@Query(value = "DELETE FROM zdb.zdbconfig WHERE namespace=:namespace AND config=:config", nativeQuery = true)
	void deleteZDBConfig(@Param("namespace") String namespace, @Param("config") String config);
}