package com.hrm.backend.repository;

import com.hrm.backend.entity.Position;
import com.hrm.backend.entity.enums.PositionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    boolean existsByNameIgnoreCaseAndStatus(String name, PositionStatus status);
    boolean existsByNameIgnoreCaseAndStatusAndIdNot(String name, PositionStatus status, Long id);

    @Query("""
            SELECT p FROM Position p
            WHERE (:q = '' OR LOWER(p.code) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR p.status = :status)
            """)
    Page<Position> searchPositions(@Param("q") String q,
                                   @Param("status") PositionStatus status,
                                   Pageable pageable);
}
