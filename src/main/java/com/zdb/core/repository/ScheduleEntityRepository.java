package com.zdb.core.repository;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.EventMetaData;
import com.zdb.core.domain.ScheduleEntity;

/**
 * Repository for ZDBEntity
 *
 * @author 06919
 *
 */
@Repository
public interface ScheduleEntityRepository extends CrudRepository<ScheduleEntity, String> {

        @Query("select t from ScheduleEntity t where scheduleId=:scheduleId" )
        ScheduleEntity findSchedule(@Param("scheduleId") String scheduleId);

        @Query("select t from ScheduleEntity t "
                        + "where namespace=:namespace "
                        + "and serviceType=:serviceType "
                        + "and serviceName=:serviceName" )
        ScheduleEntity findScheduleByName(@Param("namespace") String namespace
                        , @Param("serviceType") String serviceType
                        , @Param("serviceName") String serviceName);

        @Query("select t from ScheduleEntity t where startTime=:startTime and deleteYn='N' order by registerDate asc" )
        List<ScheduleEntity> findCurrentSchedule(@Param("startTime") String startTime);
        
        @Query("select t from ScheduleEntity t where deleteYn='N' and useYn = 'Y' and startTime <> :currentTime and incrementYn = 'Y' order by registerDate asc" )
        List<ScheduleEntity> findCurrentIncrementSchedule(@Param("currentTime") String currentTime);
        
        @Query("select t from ScheduleEntity t" )
        List<ScheduleEntity> findAll();

        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("UPDATE ScheduleEntity t SET"
                        + " t.startTime=:startTime"
                        + ", t.storePeriod=:storePeriod"
                        + ", t.deleteYn='N'"
                        + ", t.useYn=:useYn "
                        + "WHERE t.scheduleId=:scheduleId")
        int modify(@Param("startTime") String startTime
                        , @Param("storePeriod") int storePeriod
                        , @Param("useYn") String useYn
                        , @Param("scheduleId") String scheduleId);

        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("UPDATE ScheduleEntity t SET"
                        + " t.useYn='N'"
                        + ", t.deleteYn='Y'"
                        + ", t.deleteDate=now() "
                        + "WHERE t.scheduleId=:scheduleId")
        int modify2Delete(@Param("scheduleId") String scheduleId);

        @Query("select t from ScheduleEntity t "
                + "where t.namespace=:namespace "
                + "and t.useYn = 'Y' "
                + "and t.deleteYn = 'N'" )
        List<ScheduleEntity> findScheduleByNamespace(@Param("namespace") String namespace);

        @Query("select t from ScheduleEntity t "
                + "where t.useYn = 'Y' "
                + "and t.deleteYn = 'N'" )
		List<ScheduleEntity> findScheduleByNamespace();
}
