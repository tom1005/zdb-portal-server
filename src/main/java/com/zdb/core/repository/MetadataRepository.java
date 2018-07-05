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

	@Query(value="select distinct t.* from (select  name, kind, max(update_time) update_time from zdb.meta_data where namespace=:namespace and releasename=:releasename and kind=:kind and action!='DELETED'  group by name, kind  ) r  inner join zdb.meta_data t \n" + 
			"where r.name=t.name and r.kind = t.kind " + 
			"and r.update_time = t.update_time order by t.name", nativeQuery=true)
	List<MetaData> findNamespaceAndReleaseNameAndKind(@Param("namespace") String namespace, @Param("releasename") String releasename, @Param("kind") String kind);
	
	@Query(value="select distinct t.* from (select  name, kind, max(update_time) update_time from zdb.meta_data where namespace=:namespace and name=:name and kind=:kind and action!='DELETED'  group by name, kind  ) r  inner join zdb.meta_data t \n" + 
			"where r.name=t.name and r.kind = t.kind " + 
			"and r.update_time = t.update_time order by t.name limit 1", nativeQuery=true)
	MetaData findNamespaceAndNameAndKind(@Param("namespace") String namespace, @Param("name") String name, @Param("kind") String kind);
	
	@Query(value="select distinct t.* from (select  name, kind, max(update_time) update_time from zdb.meta_data where namespace=:namespace and releasename=:releasename and action!='DELETED'  group by name, kind  ) r  inner join zdb.meta_data t \n" + 
			"where r.name=t.name and r.kind = t.kind " + 
			"and r.update_time = t.update_time order by t.name", nativeQuery=true)
	List<MetaData> findNamespaceAndReleaseName(@Param("namespace") String namespace, @Param("releasename") String releasename);
	
	@Query(value="select distinct t.* from (select  name, kind, max(update_time) update_time from zdb.meta_data where namespace=:namespace and kind=:kind and action!='DELETED'  group by name, kind  ) r  inner join zdb.meta_data t \n" + 
			"where r.name=t.name and r.kind = t.kind" + 
			"and r.update_time = t.update_time order by t.name", nativeQuery=true)
	List<MetaData> findNamespaceAndKind(@Param("namespace") String namespace, @Param("kind") String kind);
	
	@Query(value="select * from zdb.meta_data where kind='Namespace' and action != 'DELETED'  order by update_time desc", nativeQuery=true)
	List<MetaData> findNamespaceAll();
	
	@Query(value="select * from zdb.meta_data where kind='Namespace' and name=:name and action != 'DELETED'  order by update_time desc limit 1", nativeQuery=true)
	MetaData findNamespace(@Param("name") String name);
	
}