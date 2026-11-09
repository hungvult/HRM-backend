package com.hrm.backend.repository;

import com.hrm.backend.entity.WorkLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkLocationRepository extends JpaRepository<WorkLocation, Long> {
}
