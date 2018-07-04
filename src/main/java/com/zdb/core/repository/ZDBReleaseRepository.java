package com.zdb.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.ReleaseMetaData;

/**
 * 
 * 
 * @author 06919
 *
 */
@Repository
public interface ZDBReleaseRepository extends CrudRepository<ReleaseMetaData, String> {
	
	@Query(value = "select * from release_meta_data t where releasename=:releasename and status != 'DELETED' order by update_time desc limit 1", nativeQuery = true)
	ReleaseMetaData findByReleaseName(@Param("releasename") final String releasename);
	
//	@Query(value = "select * from release_meta_data t where namespace=:namespace group by releasename", nativeQuery = true)
	@Query(value = "select t.* from (select releasename, max(update_time) as maxtime from zdb.release_meta_data where status != 'DELETED' group by releasename) r inner join zdb.release_meta_data t on namespace=:namespace  and t.releasename = r.releasename and t.update_time = r.maxtime and status != 'DELETED' order by t.releasename ", nativeQuery = true)
	List<ReleaseMetaData> findByNamespace(@Param("namespace") final String namespace);
	
//	@Query(value = "select * from release_meta_data t group by releasename", nativeQuery = true)
	@Query(value = "select t.* from (select releasename, max(update_time) as maxtime from zdb.release_meta_data where status != 'DELETED' group by releasename) r inner join zdb.release_meta_data t on t.releasename = r.releasename and t.update_time = r.maxtime and status != 'DELETED' order by t.releasename", nativeQuery = true)
	List<ReleaseMetaData> findAll();
}
