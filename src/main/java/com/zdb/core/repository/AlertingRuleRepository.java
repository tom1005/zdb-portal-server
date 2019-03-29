package com.zdb.core.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.Tag;

@Repository
public interface AlertingRuleRepository extends CrudRepository<Tag, String> {

//  @Query(value = "select * from zdb.tag where release_name =:release_name", nativeQuery = true)
//  List<Tag> findByAlertName(@Param("release_name") String release_name);
}