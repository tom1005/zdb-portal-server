package com.zdb.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.Tag;

/**
 * 
 * 
 * @author 06919
 *
 */
@Repository
public interface TagRepository extends CrudRepository<Tag, String> {

	@Query(value = "select * from zdb.tag where release_name =:release_name", nativeQuery = true)
	List<Tag> findByReleaseName(@Param("release_name") String release_name);
	
	@Query(value = "select * from zdb.tag where namespace =:namespace", nativeQuery = true)
	List<Tag> findByNamespace(@Param("namespace") String namespace);
	
	@Query(value = "select * from zdb.tag where namespace =:namespace and release_name =:release_name", nativeQuery = true)
	List<Tag> findByNamespaceAndReleaseName(@Param("namespace") String namespace, @Param("release_name") String release_name);
	
	@Query(value = "delete from zdb.tag where namespace =:namespace and release_name =:release_name", nativeQuery = true)
	int deleteByNamespaceAndReleaseName(@Param("namespace") String namespace, @Param("release_name") String release_name);
	
	@Query(value = "select * from zdb.tag where namespace =:namespace and release_name =:release_name and tag_name =:tag_name limit 1", nativeQuery = true)
	Tag findByNamespaceAndReleaseNameAndTag(@Param("namespace") String namespace, @Param("release_name") String release_name, @Param("tag_name") String tag_name);
	
}
