package com.zdb.core.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.Mycnf;

/**
 * 
 * 
 * @author 06919
 *
 */
@Repository
public interface MycnfRepository extends CrudRepository<Mycnf, String> {

	
}
