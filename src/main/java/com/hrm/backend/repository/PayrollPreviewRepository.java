package com.hrm.backend.repository;

import com.hrm.backend.entity.PayrollPreview;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PayrollPreviewRepository extends JpaRepository<PayrollPreview, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayrollPreview p join fetch p.ownerAccount where p.tokenHash = :tokenHash")
    Optional<PayrollPreview> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
