# CLAUDE.md - project instructions for AI coding agents

Movie ticket booking backend (Spring Boot 3.3, Java 17, JPA, H2/PostgreSQL). Read `README.md` for the design.

## Commands
- Build + all tests: `mvn clean verify`
- Run: `mvn spring-boot:run` (H2 in-memory, demo data; admin@movie.com / Admin@123)
- Single test: `mvn -Dtest=ConcurrencyIntegrationTest test`

## Architecture rules
- Layers: `controller` (HTTP, `@Valid`, principal) -> `service` (`@Transactional`, all business rules) -> `repository`.
  Controllers never touch repositories or entities; they return DTO records from `dto/*Dtos`.
- Entities never leave the service layer (`open-in-view=false`). Map to DTOs **inside** the transaction.
- Money is `BigDecimal` scale 2, `RoundingMode.HALF_UP`. Never `double`.
- Time comes from the injected `Clock` (`LocalDateTime.now(clock)`), never `LocalDateTime.now()` in business logic.
- Errors: throw `ApiException` subclasses (`NotFound`, `BadRequest`, `Conflict`, ...); `GlobalExceptionHandler` shapes the JSON.

## Non-negotiable invariants (do not break when editing)
1. **No double allocation.** Any code that changes a `ShowSeat` first row-locks it via `ShowSeatRepository.lockByIds*`
   (ordered by id). Lock order everywhere: **booking -> show-seats (by id) -> discount code**.
2. `pay`, `cancel`, `expire`, `sendReminder` lock the **booking** row first (`findByIdForUpdate`) and re-check status under the lock.
3. Releasing seats only frees seats whose `booking` still equals the booking being released (lazy-expiry takeover safety).
4. `pay` uses `noRollbackFor` for `PaymentFailedException` / `HoldExpiredException` so the FAILED payment / expiry is persisted.
5. Notifications are published as plain-data `NotificationEvent`s; delivery is `AFTER_COMMIT` + `@Async`. Never call a
   notification channel synchronously from booking code and never let delivery failures propagate.
6. Prices are snapshotted on `ShowSeat.price` at show creation; refunds are computed from the booking's paid total.
7. Only `/api/admin/**` for ADMIN and `/api/bookings/**`, `/api/notifications/**` for CUSTOMER (see `SecurityConfig`).
   Customers may only access their own bookings (return 404, not 403, for others').

## Testing conventions
- Unit tests (no Spring) in `src/test/.../unit`; full-stack tests extend `IntegrationTestBase` (profile `test`, H2).
- Each integration test creates its own uniquely named data via `createShowFixture(...)`; never rely on seeded data or test order.
- Simulate hold expiry with `backdateHold(bookingId)` then `holdExpiryService.sweep()`; do not `Thread.sleep`.
- Assert async effects with Awaitility. Scheduled jobs are disabled in tests (`app.scheduling.enabled=false`).
- Any change to booking/seat logic needs a matching concurrency or flow test.

## Style
- Constructor injection, no field `@Autowired` in main code. DTOs are records grouped in `*Dtos` holder classes.
- Keep methods small; comment the *why* for locking/ordering decisions, not the what.
