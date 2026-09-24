# API walkthrough (curl)

Start the app (`mvn spring-boot:run`). Demo data is seeded. `-u` supplies HTTP Basic credentials.

```bash
H=http://localhost:8080
ADMIN=admin@movie.com:Admin@123
CUST=customer@movie.com:Customer@123

# --- browse ----------------------------------------------------------------
curl -s -u $CUST $H/api/cities
curl -s -u $CUST "$H/api/shows?cityId=1"
curl -s -u $CUST $H/api/shows/1/seats            # note showSeatId, price and status of each seat

# --- book: hold -> pay -----------------------------------------------------
curl -s -u $CUST -X POST $H/api/bookings -H 'Content-Type: application/json' \
  -d '{"showId":1,"showSeatIds":[1,2],"discountCode":"WELCOME10"}'
# -> status PENDING_PAYMENT, holdExpiresAt (5 minutes), price breakdown

curl -s -u $CUST -X POST $H/api/bookings/1/payment -H 'Content-Type: application/json' \
  -d '{"paymentMethod":"UPI","paymentToken":"tok_123"}'      # token "fail..." simulates a decline (402)

curl -s -u $CUST $H/api/notifications           # confirmation arrives asynchronously
curl -s -u $CUST $H/api/bookings                # history

# --- try to grab the same seats as someone else -> 409 ----------------------
curl -s -u $CUST -X POST $H/api/bookings -H 'Content-Type: application/json' \
  -d '{"showId":1,"showSeatIds":[1],"discountCode":null}'

# --- cancel with refund ------------------------------------------------------
curl -s -u $CUST $H/api/bookings/1/refund-quote
curl -s -u $CUST -X POST $H/api/bookings/1/cancel -H 'Content-Type: application/json' -d '{"reason":"Plans changed"}'

# --- admin -------------------------------------------------------------------
curl -s -u $ADMIN -X POST $H/api/admin/cities -H 'Content-Type: application/json' -d '{"name":"Chennai"}'
curl -s -u $ADMIN -X POST $H/api/admin/pricing-tiers -H 'Content-Type: application/json' \
  -d '{"name":"Premiere","regularPrice":250,"premiumPrice":450,"weekendMultiplier":1.3}'
curl -s -u $ADMIN -X POST $H/api/admin/refund-policies -H 'Content-Type: application/json' \
  -d '{"name":"Strict","defaultPolicy":false,"rules":[{"minHoursBeforeShow":72,"refundPercent":100},{"minHoursBeforeShow":12,"refundPercent":40}]}'
curl -s -u $ADMIN -X POST $H/api/admin/theaters/1/screens -H 'Content-Type: application/json' \
  -d '{"name":"IMAX","rows":[{"label":"A","seatCount":12,"seatType":"REGULAR"},{"label":"B","seatCount":6,"seatType":"PREMIUM"}]}'
curl -s -u $ADMIN -X POST $H/api/admin/shows -H 'Content-Type: application/json' \
  -d '{"movieId":1,"screenId":3,"pricingTierId":2,"refundPolicyId":2,"startTime":"2026-10-03T19:30:00"}'
curl -s -u $ADMIN $H/api/admin/shows/1/stats
curl -s -u $ADMIN -X POST $H/api/admin/shows/1/cancel        # cancels + fully refunds all bookings

# --- RBAC demo ---------------------------------------------------------------
curl -s -u $CUST -X POST $H/api/admin/cities -H 'Content-Type: application/json' -d '{"name":"X"}'   # 403
curl -s $H/api/cities                                                                                # 401
```

Reproduce a race from two terminals: fire the same `POST /api/bookings` for one seat with two different customers
(register a second one via `POST /api/auth/register`) - exactly one gets `201`, the other `409`.
