package com.hrm.backend.repository;

import com.hrm.backend.entity.Department;
import com.hrm.backend.entity.enums.DepartmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByNameIgnoreCaseAndStatus(String name, DepartmentStatus status);
    boolean existsByNameIgnoreCaseAndStatusAndIdNot(String name, DepartmentStatus status, Long id);

    @Query("""
            SELECT d FROM Department d
            WHERE (:q = '' OR LOWER(d.code) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR d.status = :status)
            """)
    Page<Department> searchDepartments(@Param("q") String q, @Param("status") DepartmentStatus status, Pageable pageable);
}
