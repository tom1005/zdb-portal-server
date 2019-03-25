package com.zdb.core.repository;

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
	
	
//	select * from zdb.request_event where service_Name='zdb-test-qq' and operation='환경설정 변경' order by id desc limit 1;
	@Query(value = "select * from zdb.request_event where namespace =:namespace and service_name =:service_name and operation =:operation limit 1", nativeQuery = true)
	RequestEvent findByServiceNameAndOperation(@Param("namespace") String namespace, @Param("service_name") String service_name, @Param("operation") String operation);
	
	@Query(value = "delete from zdb.request_event where namespace =:namespace and service_name =:service_name", nativeQuery = true)
	void deleteByServiceName(@Param("namespace") String namespace, @Param("service_name") String service_name);

}
