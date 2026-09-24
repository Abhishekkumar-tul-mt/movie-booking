---
name: concurrency-review
description: Review a change touching seats, holds, payments, cancellations or expiry for race conditions, deadlocks and double allocation.
---

# Concurrency review checklist

For every changed code path ask:

- [ ] Does it modify a `ShowSeat`? Then the rows must be locked first with `lockByIds` / `lockByIdsAndShow` (ordered by id).
- [ ] Does it modify a `Booking`? Then it must start with `findByIdForUpdate` and re-check `status` (and hold deadline) after the lock.
- [ ] Lock order respected: booking -> show-seats (by id) -> discount code. Any new lock must fit this order.
- [ ] Is the decision based on data read *before* the lock? Re-read or re-validate after locking.
- [ ] Release logic frees only seats still pointing at this booking.
- [ ] Idempotency: calling pay/cancel twice must not double-charge / double-refund.
- [ ] Side effects (notifications) are published as events and delivered after commit, asynchronously.
- [ ] Exceptions that must persist state (`PaymentFailed`, `HoldExpired`) are in `noRollbackFor`; all others roll back cleanly.
- [ ] A test in `ConcurrencyIntegrationTest` (latch-released threads) or a flow test proves the new invariant.

Typical smells: `findById` followed by `save` on a seat without a lock; iterating seats in request order; catching
`Exception` around a JPA call inside the same transaction; sending email inside the transaction.
