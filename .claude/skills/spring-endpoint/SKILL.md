---
name: spring-endpoint
description: Add or change a REST endpoint in this project following its layering, validation, RBAC and error conventions.
---

# Adding an endpoint

1. **DTOs** - add request/response records to the matching holder in `dto/` (`CatalogDtos`, `PricingDtos`, `ShowDtos`, `BookingDtos`).
   Put Bean Validation on request components (`@NotBlank`, `@Min`, `@DecimalMin`, `@Valid` on nested lists).
2. **Service** - implement in the relevant `service/*Service` with `@Transactional` (`readOnly = true` for reads).
   Enforce business rules here; throw `NotFoundException` / `BadRequestException` / `ConflictException`.
   Map entities to DTOs before leaving the transaction.
3. **Controller** - thin: `@Valid @RequestBody`, `@AuthenticationPrincipal AppUserPrincipal` for the caller, `@ResponseStatus(CREATED)` for creates.
4. **RBAC** - place the path under `/api/admin/**` (ADMIN) or `/api/bookings/**` / `/api/notifications/**` (CUSTOMER), or update `SecurityConfig` deliberately. Browsing is any authenticated user.
5. **Ownership** - customer endpoints must scope by the principal's id and return 404 for other people's data.
6. **Tests** - one happy-path + one failure-path integration test extending `IntegrationTestBase`; update README API table.
7. Run `mvn clean verify`.
