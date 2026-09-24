package com.moviebooking.repository;

import com.moviebooking.domain.PricingTier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingTierRepository extends JpaRepository<PricingTier, Long> {
    boolean existsByNameIgnoreCase(String name);
}
