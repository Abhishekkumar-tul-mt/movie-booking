package com.moviebooking.service;

import com.moviebooking.config.AppProperties;
import com.moviebooking.domain.Booking;
import com.moviebooking.domain.BookingItem;
import com.moviebooking.domain.BookingStatus;
import com.moviebooking.domain.DiscountCode;
import com.moviebooking.domain.NotificationType;
import com.moviebooking.domain.Payment;
import com.moviebooking.domain.PaymentStatus;
import com.moviebooking.domain.PaymentType;
import com.moviebooking.domain.Show;
import com.moviebooking.domain.ShowSeat;
import com.moviebooking.domain.ShowSeatStatus;
import com.moviebooking.domain.ShowStatus;
import com.moviebooking.domain.User;
import com.moviebooking.dto.BookingDtos.BookingResponse;
import com.moviebooking.dto.BookingDtos.HoldRequest;
import com.moviebooking.dto.BookingDtos.PaymentRequest;
import com.moviebooking.dto.PricingDtos.RefundQuote;
import com.moviebooking.exception.BadRequestException;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.exception.HoldExpiredException;
import com.moviebooking.exception.NotFoundException;
import com.moviebooking.exception.PaymentFailedException;
import com.moviebooking.exception.SeatUnavailableException;
import com.moviebooking.notification.NotificationEvent;
import com.moviebooking.payment.GatewayResult;
import com.moviebooking.payment.PaymentGateway;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.PaymentRepository;
import com.moviebooking.repository.ShowRepository;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Core booking workflow:
 * <pre>
 *   createHold  -> PENDING_PAYMENT (seats HELD until holdExpiresAt)
 *   pay         -> CONFIRMED       (seats BOOKED)
 *   cancel      -> CANCELLED       (seats released, refund per policy if it was confirmed)
 *   expire      -> EXPIRED         (hold timed out, seats released)
 * </pre>
 * Lock order (always the same, to rule out deadlocks): booking row -> show-seat rows (by id) -> discount row.
 */
@Service
public class BookingService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private final BookingRepository bookingRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final DiscountService discountService;
    private final RefundPolicyService refundPolicyService;
    private final PaymentGateway paymentGateway;
    private final BookingMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final AppProperties props;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository, ShowRepository showRepository,
                          ShowSeatRepository showSeatRepository, UserRepository userRepository,
                          PaymentRepository paymentRepository, DiscountService discountService,
                          RefundPolicyService refundPolicyService, PaymentGateway paymentGateway,
                          BookingMapper mapper, ApplicationEventPublisher publisher, AppProperties props,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.discountService = discountService;
        this.refundPolicyService = refundPolicyService;
        this.paymentGateway = paymentGateway;
        this.mapper = mapper;
        this.publisher = publisher;
        this.props = props;
        this.clock = clock;
    }

    // =====================================================================================================
    // HOLD
    // =====================================================================================================

    /**
     * Atomically reserves the requested seats for the user. Seats are row-locked (SELECT ... FOR UPDATE, in id
     * order) so two customers racing for the same seat are serialised: the loser sees it HELD/BOOKED and gets
     * 409. A seat whose previous hold has expired is treated as free even if the sweeper has not run yet.
     */
    @Transactional
    public BookingResponse createHold(Long userId, HoldRequest req) {
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> seatIds = new LinkedHashSet<>(req.showSeatIds());
        int max = props.getBooking().getMaxSeatsPerBooking();
        if (seatIds.size() > max) {
            throw new BadRequestException("You can book at most " + max + " seats in one booking");
        }
        Show show = showRepository.findById(req.showId())
                .orElseThrow(() -> new NotFoundException("Show not found: " + req.showId()));
        if (show.getStatus() != ShowStatus.SCHEDULED) {
            throw new ConflictException("Show is not open for booking");
        }
        if (!show.getStartTime().isAfter(now)) {
            throw new BadRequestException("Show has already started");
        }

        List<ShowSeat> seats = showSeatRepository.lockByIdsAndShow(seatIds, show.getId());
        if (seats.size() != seatIds.size()) {
            throw new NotFoundException("One or more seats do not belong to this show");
        }
        List<Long> unavailable = seats.stream()
                .filter(s -> !s.isAvailableAt(now))
                .map(ShowSeat::getId)
                .toList();
        if (!unavailable.isEmpty()) {
            throw new SeatUnavailableException(unavailable);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (ShowSeat s : seats) {
            subtotal = subtotal.add(s.getPrice());
        }
        String discountCode = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (req.discountCode() != null && !req.discountCode().isBlank()) {
            DiscountService.Result r = discountService.evaluate(req.discountCode(), subtotal, now, userId);
            discountCode = r.code();
            discount = r.amount();
        }
        LocalDateTime expiresAt = now.plusMinutes(props.getHold().getDurationMinutes());

        Booking booking = new Booking();
        booking.setBookingReference("BK" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
                .toUpperCase(Locale.ROOT));
        booking.setUser(userRepository.getReferenceById(userId));
        booking.setShow(show);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setSubtotal(subtotal);
        booking.setDiscountCode(discountCode);
        booking.setDiscountAmount(discount);
        booking.setTotalAmount(subtotal.subtract(discount));
        booking.setHoldExpiresAt(expiresAt);
        booking.setCreatedAt(now);
        for (ShowSeat s : seats) {
            booking.getItems().add(new BookingItem(booking, s, s.getSeat().label(), s.getSeat().getSeatType(),
                    s.getPrice()));
        }
        bookingRepository.save(booking);
        for (ShowSeat s : seats) {
            s.hold(booking, expiresAt);
        }
        return mapper.toResponse(booking);
    }

    // =====================================================================================================
    // PAY
    // =====================================================================================================

    /**
     * Charges the customer and confirms the booking. Idempotent for already-confirmed bookings. A declined
     * payment is recorded (FAILED) and reported as 402 without releasing the hold; an expired hold is
     * released and reported as 410 - hence noRollbackFor so those state changes are committed.
     */
    @Transactional(noRollbackFor = {PaymentFailedException.class, HoldExpiredException.class})
    public BookingResponse pay(Long userId, Long bookingId, PaymentRequest req) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = lockOwnedBooking(userId, bookingId);

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return mapper.toResponse(booking);
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Booking is " + booking.getStatus() + " and cannot be paid");
        }
        if (!booking.getHoldExpiresAt().isAfter(now)) {
            expire(booking, now);
            throw new HoldExpiredException("Seat hold expired; please select your seats again");
        }

        List<ShowSeat> seats = lockSeatsOf(booking);
        for (ShowSeat s : seats) {
            boolean stillOurs = s.getStatus() == ShowSeatStatus.HELD && s.getBooking() != null
                    && s.getBooking().getId().equals(booking.getId());
            if (!stillOurs) {
                throw new ConflictException("Seat hold was lost; please select your seats again");
            }
        }

        DiscountCode discount = null;
        if (booking.getDiscountCode() != null) {
            discount = discountService.lockForRedemption(booking.getDiscountCode());
        }

        BigDecimal total = booking.getTotalAmount();
        if (total.signum() > 0) {
            GatewayResult result = paymentGateway.charge(total, req.paymentMethod(), req.paymentToken());
            paymentRepository.save(new Payment(booking, PaymentType.CHARGE,
                    result.success() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED, total, req.paymentMethod(),
                    result.reference(), result.failureReason(), now));
            if (!result.success()) {
                throw new PaymentFailedException("Payment failed: " + result.failureReason());
            }
        }

        if (discount != null) {
            discountService.redeem(discount);
        }
        for (ShowSeat s : seats) {
            s.markBooked();
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(now);

        Show show = booking.getShow();
        publish(booking, NotificationType.BOOKING_CONFIRMATION, "Booking confirmed - " + booking.getBookingReference(),
                "Your booking " + booking.getBookingReference() + " for " + show.getMovie().getTitle() + " at "
                        + show.getScreen().getTheater().getName() + " on " + show.getStartTime().format(WHEN)
                        + " is confirmed. Seats: " + seatLabels(booking) + ". Amount paid: " + total + ".");
        return mapper.toResponse(booking);
    }

    // =====================================================================================================
    // CANCEL
    // =====================================================================================================

    /**
     * Customer cancellation. PENDING bookings simply release the seats. CONFIRMED bookings are refunded
     * according to the show's refund policy (or the default one) based on time left before the show.
     */
    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = lockOwnedBooking(userId, bookingId);
        String why = reason == null || reason.isBlank() ? "Cancelled by customer" : reason.trim();

        switch (booking.getStatus()) {
            case CANCELLED:
                return mapper.toResponse(booking); // idempotent
            case EXPIRED:
                throw new ConflictException("Booking has already expired");
            case PENDING_PAYMENT:
                releaseSeats(booking);
                booking.setStatus(BookingStatus.CANCELLED);
                booking.setCancelledAt(now);
                booking.setCancellationReason(why);
                return mapper.toResponse(booking);
            default:
                break; // CONFIRMED handled below
        }

        if (!booking.getShow().getStartTime().isAfter(now)) {
            throw new ConflictException("Booking cannot be cancelled after the show has started");
        }
        RefundQuote quote = refundPolicyService.quote(booking, now);
        cancelConfirmed(booking, now, why, quote.refundAmount(), NotificationType.BOOKING_CANCELLATION,
                "Your booking " + booking.getBookingReference() + " was cancelled. Refund: " + quote.refundAmount()
                        + " (" + quote.refundPercent() + "% under policy " + quote.policyName() + ").");
        return mapper.toResponse(booking);
    }

    /** Called per booking when an admin cancels a whole show: full refund, no policy applied. */
    @Transactional
    public void cancelForShowCancellation(Long bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
        String why = "Show cancelled by theatre";
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            releaseSeats(booking);
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(now);
            booking.setCancellationReason(why);
        } else if (booking.getStatus() == BookingStatus.CONFIRMED) {
            cancelConfirmed(booking, now, why, booking.getTotalAmount(), NotificationType.SHOW_CANCELLED,
                    "Sorry - the show for booking " + booking.getBookingReference()
                            + " has been cancelled. A full refund of " + booking.getTotalAmount()
                            + " has been issued.");
        }
    }

    private void cancelConfirmed(Booking booking, LocalDateTime now, String reason, BigDecimal refundAmount,
                                 NotificationType type, String message) {
        releaseSeats(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancellationReason(reason);
        booking.setRefundAmount(refundAmount);
        if (refundAmount.signum() > 0) {
            refund(booking, refundAmount, now);
        }
        publish(booking, type, "Booking cancelled - " + booking.getBookingReference(), message);
        if (refundAmount.signum() > 0) {
            publish(booking, NotificationType.REFUND_PROCESSED, "Refund processed - " + booking.getBookingReference(),
                    "A refund of " + refundAmount + " for booking " + booking.getBookingReference()
                            + " has been issued to your original payment method.");
        }
    }

    private void refund(Booking booking, BigDecimal amount, LocalDateTime now) {
        Payment charge = paymentRepository.findByBookingIdOrderByIdAsc(booking.getId()).stream()
                .filter(p -> p.getPaymentType() == PaymentType.CHARGE && p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst().orElse(null);
        GatewayResult result = paymentGateway.refund(amount, charge == null ? null : charge.getPaymentReference());
        paymentRepository.save(new Payment(booking, PaymentType.REFUND,
                result.success() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED, amount,
                charge == null ? null : charge.getPaymentMethod(), result.reference(), result.failureReason(), now));
        if (!result.success()) {
            throw new ConflictException("Refund could not be processed; please try again");
        }
    }

    // =====================================================================================================
    // EXPIRY / REMINDER (driven by schedulers)
    // =====================================================================================================

    /** Expires one overdue hold. Re-checks under lock so it is safe against a concurrent payment. */
    @Transactional
    public boolean expireBooking(Long bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING_PAYMENT
                || booking.getHoldExpiresAt().isAfter(now)) {
            return false;
        }
        expire(booking, now);
        return true;
    }

    /** Marks the reminder as sent (exactly once) and publishes the notification. */
    @Transactional
    public boolean sendReminder(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED || booking.isReminderSent()) {
            return false;
        }
        booking.setReminderSent(true);
        Show show = booking.getShow();
        publish(booking, NotificationType.SHOW_REMINDER, "Reminder: " + show.getMovie().getTitle() + " is coming up",
                "Reminder: " + show.getMovie().getTitle() + " starts at " + show.getStartTime().format(WHEN) + " at "
                        + show.getScreen().getTheater().getName() + " (" + show.getScreen().getName()
                        + "). Seats: " + seatLabels(booking) + ". Booking " + booking.getBookingReference() + ".");
        return true;
    }

    // =====================================================================================================
    // QUERIES
    // =====================================================================================================

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getMyBooking(Long userId, Long bookingId) {
        return mapper.toResponse(findOwned(userId, bookingId));
    }

    /** What the customer would get back if they cancelled right now. */
    @Transactional(readOnly = true)
    public RefundQuote refundQuote(Long userId, Long bookingId) {
        Booking booking = findOwned(userId, bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Only confirmed bookings have a refund quote");
        }
        return refundPolicyService.quote(booking, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> adminList(Long showId) {
        List<Booking> bookings = showId == null
                ? bookingRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                : bookingRepository.findByShowIdOrderByCreatedAtDesc(showId);
        return bookings.stream().map(mapper::toResponse).toList();
    }

    // =====================================================================================================
    // helpers
    // =====================================================================================================

    private Booking findOwned(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
        if (!booking.getUser().getId().equals(userId)) {
            throw new NotFoundException("Booking not found: " + bookingId);
        }
        return booking;
    }

    private Booking lockOwnedBooking(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));
        if (!booking.getUser().getId().equals(userId)) {
            throw new NotFoundException("Booking not found: " + bookingId); // do not leak existence
        }
        return booking;
    }

    private List<ShowSeat> lockSeatsOf(Booking booking) {
        List<Long> ids = booking.getItems().stream().map(i -> i.getShowSeat().getId()).toList();
        return showSeatRepository.lockByIds(ids);
    }

    /** Frees only the seats that still point at this booking (a lazily-expired hold may have been taken over). */
    private void releaseSeats(Booking booking) {
        for (ShowSeat s : lockSeatsOf(booking)) {
            if (s.getBooking() != null && s.getBooking().getId().equals(booking.getId())) {
                s.release();
            }
        }
    }

    private void expire(Booking booking, LocalDateTime now) {
        releaseSeats(booking);
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setCancelledAt(now);
        booking.setCancellationReason("Seat hold expired before payment");
        publish(booking, NotificationType.HOLD_EXPIRED, "Seat hold expired - " + booking.getBookingReference(),
                "Your seat hold for booking " + booking.getBookingReference()
                        + " expired before payment was completed. The seats have been released.");
    }

    private String seatLabels(Booking booking) {
        return String.join(", ", booking.getItems().stream().map(BookingItem::getSeatLabel).toList());
    }

    private void publish(Booking booking, NotificationType type, String subject, String message) {
        User u = booking.getUser();
        publisher.publishEvent(new NotificationEvent(u.getId(), u.getEmail(), booking.getId(), type, subject, message));
    }
}
