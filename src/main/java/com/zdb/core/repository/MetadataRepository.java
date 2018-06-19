package com.zdb.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.MetaData;

/**

 * 
 * @author 06919
 *
 */
@Repository
public interface MetadataRepository extends CrudRepository<MetaData, String> {
	
//	@Query(value="select release_name, sum(size), sum(avail), sum(used), sum(use_rate) from zdb.disk_usage group by release_name", nativeQuery=true)
//	List<DiskUsage> findGroupByReleaseName();

//	'ConfigMap'
//	'Service'
//	'StatefulSet'
//	'PersistentVolumeClaim'
//	'Pod'
//	'Deployment'
//	'ReplicaSet'

	@Query(value="select t.* from (select name, releasename, max(update_time) as maxtime from zdb.meta_data group by name) r inner join zdb.meta_data t on namespace=:namespace and t.name = r.name and t.update_time = r.maxtime and kind=:kind and r.releasename=:releasename and action!='DELETED' order by name", nativeQuery=true)
	List<MetaData> findNamespaceAndReleaseNameAndKind(@Param("namespace") String namespace, @Param("releasename") String releasename, @Param("kind") String kind);
	
//	@Query(value="select * from zdb.meta_data where namespace=:namespace and name=:name and kind=:kind and action!='DELETED'  order by update_time desc", nativeQuery=true)
	@Query(value="select t.* from (select name, max(update_time) as maxtime from zdb.meta_data group by name) r inner join zdb.meta_data t on namespace=:namespace and t.name = r.name and t.update_time = r.maxtime and kind=:kind and r.name=:name and action!='DELETED' order by name", nativeQuery=true)
	MetaData findNamespaceAndNameAndKind(@Param("namespace") String namespace, @Param("name") String name, @Param("kind") String kind);
	
	@Query(value="select * from zdb.meta_data where namespace=:namespace and releasename=:releasename and action!='DELETED'  order by update_time desc", nativeQuery=true)
	List<MetaData> findNamespaceAndReleaseName(@Param("namespace") String namespace, @Param("releasename") String releasename);
	
	@Query(value="select * from zdb.meta_data where namespace=:namespace and kind=:kind and action!='DELETED'  order by update_time desc", nativeQuery=true)
	List<MetaData> findNamespaceAndKind(@Param("namespace") String namespace, @Param("kind") String kind);
	
}
