package com.zdb.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.UserNamespaces;

/**
 * 
 * 
 * @author 06919
 *
 */
@Repository
public interface UserNamespaceRepository extends CrudRepository<UserNamespaces, String> {

	@Query(value = "select * from zdb.user_namespaces where user_id =:user_id", nativeQuery = true)
	List<UserNamespaces> findByUserId(@Param("user_id") String user_id);
}
