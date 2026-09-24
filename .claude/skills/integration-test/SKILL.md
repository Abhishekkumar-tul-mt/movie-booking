---
name: integration-test
description: Write a full-stack integration test for this project (MockMvc + H2 + real security) using the shared fixtures.
---

# Writing an integration test

1. Extend `IntegrationTestBase` (profile `test`, H2, scheduling disabled, no demo data).
2. Build data with `createShowFixture(hoursFromNow(72))` -> `Fixture(showId, regularSeatIds[8], premiumSeatIds[4])`
   (regular 200, premium 350, weekend multiplier 1.00 so prices are weekday-independent). Users: `createCustomer()`, `createAdmin()`.
3. Call the API with `post/get(url, email, body)` (HTTP Basic is added for you) or helpers `hold`, `pay`, `holdAndPay`.
   Parse with `read(result, X.class)`; assert money with `isEqualByComparingTo("400.00")`.
4. Time-based behaviour: choose the show start (`hoursFromNow(n)`) to hit a refund tier; simulate hold expiry with
   `backdateHold(bookingId)` + `holdExpiryService.sweep()`. Never `Thread.sleep`.
5. Async effects (notifications): wrap in Awaitility `await().atMost(...).untilAsserted(...)`.
6. Concurrency: use a start latch so all threads fire together; assert on database state, not just return values.
