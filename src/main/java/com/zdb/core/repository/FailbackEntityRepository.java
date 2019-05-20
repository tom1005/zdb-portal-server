package com.zdb.core.repository;
 
import java.util.Date;
 
import javax.transaction.Transactional;
 
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.FailbackEntity;
 
@Repository
public interface FailbackEntityRepository extends CrudRepository<FailbackEntity, String> {
     
    @Query("select t from FailbackEntity t where failbackId=:failbackId" )
    FailbackEntity findFailback(@Param("failbackId") String failbackId);
    
    @Query(value =  "select t from failback_entity t "
            + " where namespace=:namespace "
            + " and service_type=:serviceType "
            + " and service_name=:serviceName "
            + " order by accepted_datetime desc limit 1" , nativeQuery = true)
    FailbackEntity findFailbackByName(@Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
    
    
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE FailbackEntity t SET"
            + " t.startDatetime=:startDatetime"
            + " , t.status='START'"
            + " WHERE t.failbackId=:failbackId")
    int modify2Started(@Param("startDatetime") Date startDatetime
            , @Param("failbackId") String failbackId);
    
    
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE FailbackEntity t SET"
    		+ " t.status=:status"
    		+ " WHERE t.failbackId=:failbackId")
    int modify2Status(@Param("status") String status
            , @Param("failbackId") String failbackId);
    
    
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE FailbackEntity t SET"
    		+ " t.status='FAILED'"
    		+ " , t.reason = :reason"
    		+ " WHERE t.failbackId=:failbackId")
    int modify2Failed(@Param("reason") String reason
            , @Param("failbackId") String failbackId);
     
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE FailbackEntity t SET"
            + " t.completeDatetime=:completeDatetime"
            + " , t.status='COMPLETE'"
            + " WHERE t.failbackId=:failbackId")
    int modify2Completed(@Param("completeDatetime") Date completeDatetime
            , @Param("failbackId") String failbackId);
 
}