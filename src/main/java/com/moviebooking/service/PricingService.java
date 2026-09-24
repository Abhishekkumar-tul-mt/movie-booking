package com.moviebooking.service;

import com.moviebooking.domain.PricingTier;
import com.moviebooking.domain.SeatType;
import com.moviebooking.dto.PricingDtos.PricingTierRequest;
import com.moviebooking.dto.PricingDtos.PricingTierResponse;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.exception.NotFoundException;
import com.moviebooking.repository.PricingTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PricingService {

    private final PricingTierRepository repository;

    public PricingService(PricingTierRepository repository) {
        this.repository = repository;
    }

    /**
     * Base price for the seat type, multiplied by the tier's weekend multiplier when the show starts on a
     * Saturday or Sunday. Prices are snapshotted onto each ShowSeat at show creation, so later tier edits
     * do not change already-scheduled shows.
     */
    public BigDecimal priceFor(SeatType seatType, PricingTier tier, LocalDateTime showStart) {
        BigDecimal base = seatType == SeatType.PREMIUM ? tier.getPremiumPrice() : tier.getRegularPrice();
        DayOfWeek day = showStart.getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        BigDecimal price = weekend ? base.multiply(tier.getWeekendMultiplier()) : base;
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public PricingTierResponse create(PricingTierRequest req) {
        String name = req.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Pricing tier already exists: " + name);
        }
        PricingTier tier = new PricingTier();
        apply(tier, name, req);
        return toResponse(repository.save(tier));
    }

    @Transactional
    public PricingTierResponse update(Long id, PricingTierRequest req) {
        PricingTier tier = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pricing tier not found: " + id));
        String name = req.name().trim();
        if (!tier.getName().equalsIgnoreCase(name) && repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Pricing tier already exists: " + name);
        }
        apply(tier, name, req);
        return toResponse(tier);
    }

    @Transactional(readOnly = true)
    public List<PricingTierResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    private void apply(PricingTier tier, String name, PricingTierRequest req) {
        tier.setName(name);
        tier.setRegularPrice(req.regularPrice().setScale(2, RoundingMode.HALF_UP));
        tier.setPremiumPrice(req.premiumPrice().setScale(2, RoundingMode.HALF_UP));
        tier.setWeekendMultiplier(req.weekendMultiplier().setScale(2, RoundingMode.HALF_UP));
    }

    private PricingTierResponse toResponse(PricingTier t) {
        return new PricingTierResponse(t.getId(), t.getName(), t.getRegularPrice(), t.getPremiumPrice(),
                t.getWeekendMultiplier());
    }
}
