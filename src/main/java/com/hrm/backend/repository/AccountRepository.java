package com.hrm.backend.repository;

import com.hrm.backend.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("SELECT a FROM Account a WHERE LOWER(a.username) = LOWER(:usernameOrEmail) OR LOWER(a.email) = LOWER(:usernameOrEmail)")
    Optional<Account> findByUsernameIgnoreCaseOrEmailIgnoreCase(@Param("usernameOrEmail") String usernameOrEmail);
    
    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.employee WHERE a.id = :id")
    Optional<Account> findByIdWithEmployee(@Param("id") Long id);
}
