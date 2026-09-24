package com.moviebooking.service;

import com.moviebooking.domain.BookingStatus;
import com.moviebooking.domain.DiscountCode;
import com.moviebooking.domain.DiscountType;
import com.moviebooking.dto.PricingDtos.DiscountCodeRequest;
import com.moviebooking.dto.PricingDtos.DiscountCodeResponse;
import com.moviebooking.exception.BadRequestException;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.exception.NotFoundException;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.DiscountCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class DiscountService {

    /** Outcome of evaluating a code against an order. */
    public record Result(String code, BigDecimal amount) {
    }

    private final DiscountCodeRepository repository;
    private final BookingRepository bookingRepository;

    public DiscountService(DiscountCodeRepository repository, BookingRepository bookingRepository) {
        this.repository = repository;
        this.bookingRepository = bookingRepository;
    }

    public static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /** Validates the code for this order and computes the discount. Does not consume a redemption. */
    @Transactional(readOnly = true)
    public Result evaluate(String rawCode, BigDecimal subtotal, LocalDateTime now, Long userId) {
        String code = normalize(rawCode);
        DiscountCode dc = repository.findByCode(code)
                .orElseThrow(() -> new BadRequestException("Invalid discount code"));
        if (!dc.isActive()) {
            throw new BadRequestException("Discount code is not active");
        }
        if (now.isBefore(dc.getValidFrom()) || now.isAfter(dc.getValidTo())) {
            throw new BadRequestException("Discount code is not valid at this time");
        }
        if (dc.getUsageLimit() != null && dc.getUsedCount() >= dc.getUsageLimit()) {
            throw new BadRequestException("Discount code usage limit reached");
        }
        if (dc.getMinOrderAmount() != null && subtotal.compareTo(dc.getMinOrderAmount()) < 0) {
            throw new BadRequestException("Minimum order amount for this code is " + dc.getMinOrderAmount());
        }
        if (dc.getPerUserLimit() != null && userId != null) {
            long used = bookingRepository.countUserDiscountUsage(userId, code,
                    List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED));
            if (used >= dc.getPerUserLimit()) {
                throw new BadRequestException("You have already used this discount code the maximum number of times");
            }
        }
        return new Result(code, computeAmount(dc, subtotal));
    }

    public BigDecimal computeAmount(DiscountCode dc, BigDecimal subtotal) {
        BigDecimal amount;
        if (dc.getDiscountType() == DiscountType.PERCENTAGE) {
            amount = subtotal.multiply(dc.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (dc.getMaxDiscountAmount() != null && amount.compareTo(dc.getMaxDiscountAmount()) > 0) {
                amount = dc.getMaxDiscountAmount();
            }
        } else {
            amount = dc.getDiscountValue();
        }
        if (amount.compareTo(subtotal) > 0) {
            amount = subtotal;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Row-locks the code and checks the global limit; the lock is held until the caller's transaction ends, so
     * the later {@link #redeem} cannot oversell the limit. Call inside the payment transaction.
     */
    public DiscountCode lockForRedemption(String code) {
        DiscountCode dc = repository.findByCodeForUpdate(code)
                .orElseThrow(() -> new ConflictException("Discount code no longer exists"));
        if (dc.getUsageLimit() != null && dc.getUsedCount() >= dc.getUsageLimit()) {
            throw new ConflictException("Discount code usage limit reached; please rebook without it");
        }
        return dc;
    }

    public void redeem(DiscountCode dc) {
        dc.setUsedCount(dc.getUsedCount() + 1);
    }

    // ------------------------------------------------------------ admin CRUD

    @Transactional
    public DiscountCodeResponse create(DiscountCodeRequest req) {
        String code = normalize(req.code());
        if (repository.existsByCode(code)) {
            throw new ConflictException("Discount code already exists: " + code);
        }
        DiscountCode dc = new DiscountCode();
        dc.setCode(code);
        apply(dc, req);
        return toResponse(repository.save(dc));
    }

    @Transactional
    public DiscountCodeResponse update(Long id, DiscountCodeRequest req) {
        DiscountCode dc = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Discount code not found: " + id));
        if (!dc.getCode().equals(normalize(req.code()))) {
            throw new BadRequestException("Discount code text cannot be changed; create a new code instead");
        }
        apply(dc, req);
        return toResponse(dc);
    }

    @Transactional(readOnly = true)
    public List<DiscountCodeResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    private void apply(DiscountCode dc, DiscountCodeRequest req) {
        if (req.discountType() == DiscountType.PERCENTAGE && req.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("Percentage discount cannot exceed 100");
        }
        if (req.validTo().isBefore(req.validFrom())) {
            throw new BadRequestException("validTo must not be before validFrom");
        }
        dc.setDiscountType(req.discountType());
        dc.setDiscountValue(req.discountValue().setScale(2, RoundingMode.HALF_UP));
        dc.setMaxDiscountAmount(req.maxDiscountAmount());
        dc.setMinOrderAmount(req.minOrderAmount());
        dc.setValidFrom(req.validFrom());
        dc.setValidTo(req.validTo());
        dc.setUsageLimit(req.usageLimit());
        dc.setPerUserLimit(req.perUserLimit());
        dc.setActive(req.active() == null || req.active());
    }

    private DiscountCodeResponse toResponse(DiscountCode d) {
        return new DiscountCodeResponse(d.getId(), d.getCode(), d.getDiscountType(), d.getDiscountValue(),
                d.getMaxDiscountAmount(), d.getMinOrderAmount(), d.getValidFrom(), d.getValidTo(),
                d.getUsageLimit(), d.getPerUserLimit(), d.getUsedCount(), d.isActive());
    }
}
