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
	
//	@Query("select t from EventMetaData t where name=:name and message=:message and last_timestamp=:last_timestamp" )
//	EventMetaData findByTxId(@Param("last_timestamp") String last_timestamp);
	
	@Query(value = "select id, date_format(first_timestamp, '%Y-%m-%d %T') as first_timestamp, date_format(last_timestamp, '%Y-%m-%d %T') as last_timestamp, kind, message, metadata, name, namespace, reason, uid, release_name from event_meta_data t where name=:name and message=:message and last_timestamp=:lastTimestamp limit 1", nativeQuery = true)
	EventMetaData findByNameAndMessageAndLastTimestamp(@Param("name") final String name, @Param("message") final String message, @Param("lastTimestamp") final String lastTimestamp);
	
	@Query(value = "select id, date_format(first_timestamp, '%Y-%m-%d %T') as first_timestamp, date_format(last_timestamp, '%Y-%m-%d %T') as last_timestamp, kind, message, metadata, name, namespace, reason, uid, release_name from event_meta_data t where name like :name and kind=:kind and reason=:reason order by last_timestamp ", nativeQuery = true)
	List<EventMetaData> findByKindAndNameAndReason(@Param("kind") final String kind, @Param("name") final String name, @Param("reason") final String reason);
//	
//	@Query(value = "select id, date_format(first_timestamp, '%Y-%m-%d %T') as first_timestamp, date_format(last_timestamp, '%Y-%m-%d %T') as last_timestamp, kind, '' as metadata, message, name, namespace, reason, uid from event_meta_data t where namespace=:namespace and kind=:kind and name like %:name% order by last_timestamp ", nativeQuery = true)
//	List<EventMetaData> findByNamespaceAndKindAndName(@Param("namespace") final String namespace, @Param("kind") final String kind, @Param("name") final String name);
//
//	@Query(value = "select id, date_format(first_timestamp, '%Y-%m-%d %T') as first_timestamp, date_format(last_timestamp, '%Y-%m-%d %T') as last_timestamp, kind, '' as metadata, message, name, namespace, reason, uid from event_meta_data t where namespace=:namespace and kind=:kind order by last_timestamp ", nativeQuery = true)
//	List<EventMetaData> findByNamespaceAndKind(@Param("namespace") final String namespace, @Param("kind") final String kind);
//
//	@Query(value = "select id, date_format(first_timestamp, '%Y-%m-%d %T') as first_timestamp, date_format(last_timestamp, '%Y-%m-%d %T') as last_timestamp, kind, '' as metadata, message, name, namespace, reason, uid from event_meta_data t where namespace=:namespace and name like %:name% order by last_timestamp ", nativeQuery = true)
//	List<EventMetaData> findByNamespaceAndName(@Param("namespace") final String namespace, @Param("name") final String name);
//
//	@Query(value = "select id, date_format(first_timestamp, '%Y-%m-%d %T') as first_timestamp, date_format(last_timestamp, '%Y-%m-%d %T') as last_timestamp, kind, '' as metadata, message, name, namespace, reason, uid from event_meta_data t where namespace=:namespace order by last_timestamp ", nativeQuery = true)
//	List<EventMetaData> findByNamespace(@Param("namespace") final String namespace);

//
//	@Query("select t from RequestEvent t where serviceName=:serviceName and eventType=:eventType and startTime = (select max(d.startTime) from RequestEvent d where serviceName=:serviceName and event_type=:eventType)" )
//	RequestEvent findRequestEvent(@Param("serviceName") String serviceName, @Param("eventType") String eventType);
//
//	@Query("select count(tx_id) from RequestEvent t where tx_id=:tx_id" )
//	int getCount(@Param("tx_id") String tx_id);
//	
//	
//	@Modifying
//	@Transactional
//	@Query("update RequestEvent set update_time=:update_time, endtime=:endtime, status=:status, result_message=:result_message  where tx_id=:tx_id" )
//	Integer update(@Param("tx_id") String txId, @Param("update_time") Date updateTime, @Param("endtime") Date endTime, @Param("status") Integer status, @Param("result_message") String message);
}
