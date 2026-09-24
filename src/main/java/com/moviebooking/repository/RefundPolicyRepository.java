package com.moviebooking.repository;

import com.moviebooking.domain.RefundPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {
    Optional<RefundPolicy> findFirstByDefaultPolicyTrue();

    boolean existsByNameIgnoreCase(String name);
}
