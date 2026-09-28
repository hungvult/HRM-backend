package com.hrm.backend.repository;

import com.hrm.backend.entity.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRole.AccountRoleId> {

    @Query("SELECT ar FROM AccountRole ar JOIN FETCH ar.role WHERE ar.account.id = :accountId")
    List<AccountRole> findByAccountIdWithRole(@Param("accountId") Long accountId);

    @Query("SELECT COUNT(ar) FROM AccountRole ar WHERE ar.role.code = :roleCode")
    long countByRoleCode(@Param("roleCode") String roleCode);
}
