package com.zdb.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zdb.core.domain.SlaveStatus;

public interface SlaveStatusRepository extends JpaRepository<SlaveStatus, String> {
}
