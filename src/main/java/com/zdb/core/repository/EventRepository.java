package com.zdb.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.EventMetaData;

/**
 * Repository for ZDBEntity
 * 
 * @author 06919
 *
 */
@Repository
public interface EventRepository extends CrudRepository<EventMetaData, String> {
	@Query(value = "select id, date_format(first_timestamp, '%Y-%m-%d %T') as first_timestamp, date_format(last_timestamp, '%Y-%m-%d %T') as last_timestamp, kind, message, metadata, name, namespace, reason, uid, release_name from event_meta_data t where name=:name and message=:message and last_timestamp=:lastTimestamp limit 1", nativeQuery = true)
	EventMetaData findByNameAndMessageAndLastTimestamp(@Param("name") final String name, @Param("message") final String message, @Param("lastTimestamp") final String lastTimestamp);
	
	@Query(value = "select id, date_format(first_timestamp, '%Y-%m-%d %T') as first_timestamp, date_format(last_timestamp, '%Y-%m-%d %T') as last_timestamp, kind, message, metadata, name, namespace, reason, uid, release_name from event_meta_data t where name like :name and kind=:kind and reason=:reason order by last_timestamp ", nativeQuery = true)
	List<EventMetaData> findByKindAndNameAndReason(@Param("kind") final String kind, @Param("name") final String name, @Param("reason") final String reason);
	
	@Query(value = "select id, date_format(first_timestamp, '%Y-%m-%d %T') as first_timestamp, date_format(last_timestamp, '%Y-%m-%d %T') as last_timestamp, kind, message, metadata, name, namespace, reason, uid, release_name from event_meta_data t where name like :name and kind=:kind order by last_timestamp desc limit 1 ", nativeQuery = true)
	EventMetaData findByKindAndName(@Param("kind") final String kind, @Param("name") final String name);
}
