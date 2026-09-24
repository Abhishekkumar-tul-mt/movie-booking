# 10-minute video outline

| Time | Section | Show |
|---|---|---|
| 0:00-1:00 | **Problem & scoping** | Requirement; what I built (README top), what I deliberately left out (section 9) |
| 1:00-2:00 | **Stack & why** | Spring Boot/JPA/Security, H2 default + Postgres profile, no frontend, mock payment port |
| 2:00-3:30 | **Domain model** | ER diagram; ShowSeat as the bookable unit; price snapshot; booking = hold -> confirm |
| 3:30-6:00 | **Concurrency (the heart)** | `ShowSeatRepository.lockByIds` (FOR UPDATE, ordered by id), lock order, `@Version` + unique constraint as safety nets, lazy + sweeper expiry, `pay` idempotency |
| 6:00-7:00 | **Pricing / discounts / refunds / notifications** | Tier + weekend multiplier, refund rules, admin show-cancel, after-commit async events |
| 7:00-8:00 | **Live demo** | curl walkthrough: hold -> 409 for second user -> pay -> notification -> cancel with refund -> RBAC 403 |
| 8:00-9:00 | **Testing** | unit vs integration vs concurrency suites; run `mvn clean verify`; latch-based 20-thread test |
| 9:00-10:00 | **AI workflow** | CLAUDE.md invariants, skills, what AI generated vs what I reviewed/changed, bugs caught, next steps |
