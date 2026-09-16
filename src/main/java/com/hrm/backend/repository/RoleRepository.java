package com.hrm.backend.repository;

import com.hrm.backend.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByCodeIn(Collection<String> codes);
}
