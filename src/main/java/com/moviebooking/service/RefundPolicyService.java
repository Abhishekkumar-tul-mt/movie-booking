package com.moviebooking.service;

import com.moviebooking.domain.Booking;
import com.moviebooking.domain.RefundPolicy;
import com.moviebooking.domain.RefundRule;
import com.moviebooking.domain.Show;
import com.moviebooking.dto.PricingDtos.RefundPolicyRequest;
import com.moviebooking.dto.PricingDtos.RefundPolicyResponse;
import com.moviebooking.dto.PricingDtos.RefundQuote;
import com.moviebooking.dto.PricingDtos.RefundRuleDto;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.exception.NotFoundException;
import com.moviebooking.repository.RefundPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RefundPolicyService {

    private final RefundPolicyRepository repository;

    public RefundPolicyService(RefundPolicyRepository repository) {
        this.repository = repository;
    }

    /**
     * Picks the rule with the largest "minHoursBeforeShow" that the cancellation still satisfies.
     * Example rules {48h:100, 24h:50, 2h:25}: 30h before -> 50%, 1h before -> 0%.
     */
    public int computePercent(List<RefundRule> rules, long minutesUntilShow) {
        return rules.stream()
                .sorted(Comparator.comparingInt(RefundRule::getMinHoursBeforeShow).reversed())
                .filter(r -> minutesUntilShow >= r.getMinHoursBeforeShow() * 60L)
                .mapToInt(RefundRule::getRefundPercent)
                .findFirst()
                .orElse(0);
    }

    public BigDecimal applyPercent(BigDecimal amount, int percent) {
        return amount.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Must be called inside a transaction (touches lazy associations of the booking). */
    public RefundQuote quote(Booking booking, LocalDateTime now) {
        Show show = booking.getShow();
        RefundPolicy policy = resolvePolicy(show);
        long minutes = Duration.between(now, show.getStartTime()).toMinutes();
        int percent = policy == null ? 0 : computePercent(policy.getRules(), minutes);
        BigDecimal paid = booking.getTotalAmount();
        return new RefundQuote(booking.getId(), paid, percent, applyPercent(paid, percent),
                policy == null ? "NO_POLICY" : policy.getName(), minutes);
    }

    private RefundPolicy resolvePolicy(Show show) {
        if (show.getRefundPolicy() != null) {
            return show.getRefundPolicy();
        }
        return repository.findFirstByDefaultPolicyTrue().orElse(null);
    }

    @Transactional
    public RefundPolicyResponse create(RefundPolicyRequest req) {
        String name = req.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Refund policy already exists: " + name);
        }
        RefundPolicy policy = new RefundPolicy();
        apply(policy, name, req);
        return toResponse(repository.save(policy));
    }

    @Transactional
    public RefundPolicyResponse update(Long id, RefundPolicyRequest req) {
        RefundPolicy policy = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Refund policy not found: " + id));
        String name = req.name().trim();
        if (!policy.getName().equalsIgnoreCase(name) && repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Refund policy already exists: " + name);
        }
        apply(policy, name, req);
        return toResponse(policy);
    }

    @Transactional(readOnly = true)
    public List<RefundPolicyResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public boolean hasDefault() {
        return repository.findFirstByDefaultPolicyTrue().isPresent();
    }

    private void apply(RefundPolicy policy, String name, RefundPolicyRequest req) {
        policy.setName(name);
        if (req.defaultPolicy()) {
            // keep the "at most one default" invariant
            for (RefundPolicy other : repository.findAll()) {
                if (other.isDefaultPolicy() && !other.equals(policy)) {
                    other.setDefaultPolicy(false);
                }
            }
        }
        policy.setDefaultPolicy(req.defaultPolicy());
        List<RefundRule> rules = new ArrayList<>();
        for (RefundRuleDto r : req.rules()) {
            rules.add(new RefundRule(r.minHoursBeforeShow(), r.refundPercent()));
        }
        policy.getRules().clear();
        policy.getRules().addAll(rules);
    }

    private RefundPolicyResponse toResponse(RefundPolicy p) {
        List<RefundRuleDto> rules = p.getRules().stream()
                .sorted(Comparator.comparingInt(RefundRule::getMinHoursBeforeShow).reversed())
                .map(r -> new RefundRuleDto(r.getMinHoursBeforeShow(), r.getRefundPercent()))
                .toList();
        return new RefundPolicyResponse(p.getId(), p.getName(), p.isDefaultPolicy(), rules);
    }
}
