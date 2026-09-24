package com.moviebooking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "refund_policies")
@Getter
@Setter
@NoArgsConstructor
public class RefundPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Applied to shows that do not reference an explicit policy. At most one policy is the default. */
    @Column(nullable = false)
    private boolean defaultPolicy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "refund_policy_rules", joinColumns = @JoinColumn(name = "policy_id"))
    private List<RefundRule> rules = new ArrayList<>();
}
