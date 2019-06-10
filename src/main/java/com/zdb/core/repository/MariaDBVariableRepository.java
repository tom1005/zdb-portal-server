package com.zdb.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.MariaDBVariable;

@Repository
public interface MariaDBVariableRepository  extends JpaRepository<MariaDBVariable, String> {
													  
}
