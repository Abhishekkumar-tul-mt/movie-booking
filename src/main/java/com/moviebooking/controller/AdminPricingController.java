package com.moviebooking.controller;

import com.moviebooking.dto.PricingDtos.DiscountCodeRequest;
import com.moviebooking.dto.PricingDtos.DiscountCodeResponse;
import com.moviebooking.dto.PricingDtos.PricingTierRequest;
import com.moviebooking.dto.PricingDtos.PricingTierResponse;
import com.moviebooking.dto.PricingDtos.RefundPolicyRequest;
import com.moviebooking.dto.PricingDtos.RefundPolicyResponse;
import com.moviebooking.service.DiscountService;
import com.moviebooking.service.PricingService;
import com.moviebooking.service.RefundPolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ADMIN only: pricing tiers, refund policies and discount codes. */
@RestController
@RequestMapping("/api/admin")
public class AdminPricingController {

    private final PricingService pricingService;
    private final RefundPolicyService refundPolicyService;
    private final DiscountService discountService;

    public AdminPricingController(PricingService pricingService, RefundPolicyService refundPolicyService,
                                  DiscountService discountService) {
        this.pricingService = pricingService;
        this.refundPolicyService = refundPolicyService;
        this.discountService = discountService;
    }

    @PostMapping("/pricing-tiers")
    @ResponseStatus(HttpStatus.CREATED)
    public PricingTierResponse createTier(@Valid @RequestBody PricingTierRequest req) {
        return pricingService.create(req);
    }

    @PutMapping("/pricing-tiers/{id}")
    public PricingTierResponse updateTier(@PathVariable Long id, @Valid @RequestBody PricingTierRequest req) {
        return pricingService.update(id, req);
    }

    @GetMapping("/pricing-tiers")
    public List<PricingTierResponse> tiers() {
        return pricingService.list();
    }

    @PostMapping("/refund-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundPolicyResponse createPolicy(@Valid @RequestBody RefundPolicyRequest req) {
        return refundPolicyService.create(req);
    }

    @PutMapping("/refund-policies/{id}")
    public RefundPolicyResponse updatePolicy(@PathVariable Long id, @Valid @RequestBody RefundPolicyRequest req) {
        return refundPolicyService.update(id, req);
    }

    @GetMapping("/refund-policies")
    public List<RefundPolicyResponse> policies() {
        return refundPolicyService.list();
    }

    @PostMapping("/discount-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public DiscountCodeResponse createDiscount(@Valid @RequestBody DiscountCodeRequest req) {
        return discountService.create(req);
    }

    @PutMapping("/discount-codes/{id}")
    public DiscountCodeResponse updateDiscount(@PathVariable Long id, @Valid @RequestBody DiscountCodeRequest req) {
        return discountService.update(id, req);
    }

    @GetMapping("/discount-codes")
    public List<DiscountCodeResponse> discounts() {
        return discountService.list();
    }
}
