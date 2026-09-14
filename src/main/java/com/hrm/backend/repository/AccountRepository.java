package com.hrm.backend.repository;

import com.hrm.backend.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.hrm.backend.entity.enums.AccountStatus;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("SELECT a FROM Account a WHERE LOWER(a.username) = LOWER(:usernameOrEmail) OR LOWER(a.email) = LOWER(:usernameOrEmail)")
    Optional<Account> findByUsernameIgnoreCaseOrEmailIgnoreCase(@Param("usernameOrEmail") String usernameOrEmail);
    
    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.employee WHERE a.id = :id")
    Optional<Account> findByIdWithEmployee(@Param("id") Long id);

    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT a FROM Account a WHERE (:keyword = '' OR LOWER(a.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND (:status IS NULL OR a.status = :status) AND (:roleCode = '' OR EXISTS (SELECT ar FROM AccountRole ar WHERE ar.account = a AND ar.role.code = :roleCode))")
    Page<Account> search(@Param("keyword") String keyword, @Param("status") AccountStatus status, @Param("roleCode") String roleCode, Pageable pageable);
}
