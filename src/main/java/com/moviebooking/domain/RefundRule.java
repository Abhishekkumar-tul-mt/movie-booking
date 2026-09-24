package com.moviebooking.domain;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** "If cancelled at least {minHoursBeforeShow} hours before the show, refund {refundPercent}%". */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefundRule {

    private int minHoursBeforeShow;

    private int refundPercent;
}
