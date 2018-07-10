package com.zdb.core.repository;

import java.util.Date;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.RequestEvent;

/**
 * Repository for ZDBEntity
 * 
 * @author 06919
 *
 */
@Repository
public interface ZDBRepository extends CrudRepository<RequestEvent, String> {
	
	@Query("select t from RequestEvent t where tx_id=:tx_id" )
	RequestEvent findByTxId(@Param("tx_id") String tx_id);

	@Query("select t from RequestEvent t where serviceName=:serviceName and eventType=:eventType and startTime = (select max(d.startTime) from RequestEvent d where serviceName=:serviceName and event_type=:eventType)" )
	RequestEvent findRequestEvent(@Param("serviceName") String serviceName, @Param("eventType") String eventType);

	@Query("select count(tx_id) from RequestEvent t where tx_id=:tx_id" )
	int getCount(@Param("tx_id") String tx_id);
	
	@Modifying
	@Transactional
	@Query("update RequestEvent set update_time=:update_time, endtime=:endtime, status=:status, result_message=:result_message  where tx_id=:tx_id" )
	Integer update(@Param("tx_id") String txId, @Param("update_time") Date updateTime, @Param("endtime") Date endTime, @Param("status") Integer status, @Param("result_message") String message);
}
