package com.hrm.backend.repository;

import com.hrm.backend.entity.WorkLocationWifiNetwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkLocationWifiNetworkRepository extends JpaRepository<WorkLocationWifiNetwork, Long> {

    @Query("""
            select network from WorkLocationWifiNetwork network
            join fetch network.workLocation location
            where network.id = :id and network.isActive = true and location.isActive = true
            """)
    Optional<WorkLocationWifiNetwork> findActiveWithLocationById(@Param("id") Long id);
}
