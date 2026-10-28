package com.hrm.backend.repository;

import com.hrm.backend.entity.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.enums.AccountStatus;

@Repository
public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRole.AccountRoleId> {

    @Query("SELECT ar FROM AccountRole ar JOIN FETCH ar.role WHERE ar.account.id = :accountId")
    List<AccountRole> findByAccountIdWithRole(@Param("accountId") Long accountId);

    @Query("SELECT COUNT(ar) FROM AccountRole ar WHERE ar.role.code = :roleCode")
    long countByRoleCode(@Param("roleCode") String roleCode);

    @Query("SELECT COUNT(ar) > 0 FROM AccountRole ar WHERE ar.account.id = :accountId AND ar.role.code = :roleCode")
    boolean hasRole(@Param("accountId") Long accountId, @Param("roleCode") String roleCode);

    @Query("""
            SELECT ar.account FROM AccountRole ar
            WHERE ar.role.code = :roleCode AND ar.account.status = :accountStatus
            """)
    List<Account> findAccountsByRoleCodeAndAccountStatus(@Param("roleCode") String roleCode,
                                                          @Param("accountStatus") AccountStatus accountStatus);
}
