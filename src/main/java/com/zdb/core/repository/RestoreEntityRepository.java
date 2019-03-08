package com.zdb.core.repository;
 
import java.util.Date;
import java.util.List;
 
import javax.transaction.Transactional;
 
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
import com.zdb.core.domain.RestoreEntity;
 
@Repository
public interface RestoreEntityRepository extends CrudRepository<RestoreEntity, String> {
     
    @Query("select t from RestoreEntity t where restoreId=:restoreId" )
    RestoreEntity findRestore(@Param("restoreId") String restoreId);
 
    @Query("select t from RestoreEntity t where serviceType=:serviceType and serviceName=:serviceName")
    List<RestoreEntity> findRestoreByService(@Param("serviceType") String serviceType
            , @Param("serviceName") String serviceName);
     
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE RestoreEntity t SET"
            + " t.startDatetime=:startDatetime"
            + " , t.status='RESTORE'"
            + " WHERE t.restoreId=:restoreId")
    int modify2Started(@Param("startDatetime") Date startDatetime
            , @Param("restoreId") String restoreId);
     
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE RestoreEntity t SET"
    		+ " t.status='FAILED'"
    		+ " , t.reason = :reason"
    		+ " WHERE t.restoreId=:restoreId")
    int modify2Failed(@Param("reason") String reason
            , @Param("restoreId") String restoreId);
     
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE RestoreEntity t SET"
            + " t.completeDatetime=:completeDatetime"
            + " , t.status='COMPLETE'"
            + " WHERE t.restoreId=:restoreId")
    int modify2Completed(@Param("completeDatetime") Date completeDatetime
            , @Param("restoreId") String restoreId);
 
}