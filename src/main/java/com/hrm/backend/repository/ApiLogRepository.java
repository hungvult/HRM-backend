package com.hrm.backend.repository;

import com.hrm.backend.entity.ApiLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiLogRepository extends JpaRepository<ApiLog, Long> {
}
