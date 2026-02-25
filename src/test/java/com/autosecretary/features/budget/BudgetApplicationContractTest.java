package com.autosecretary.features.budget;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Application-Tests für Import-Duplikate und Buchungsänderungen.
 */
public class BudgetApplicationContractTest {

    @Test
    public void importDuplicates_areIgnoredByHash() {
        InMemoryLedger ledger = new InMemoryLedger();
        ledger.putAccount(new AccountState(1L, 250_000));

        ImportService service = new ImportService(ledger);
        List<ParsedBooking> parsed = List.of(
            new ParsedBooking("h-1", 1L, -4_532, LocalDate.of(2026, 2, 1), 12L),
            new ParsedBooking("h-2", 1L, -2_850, LocalDate.of(2026, 2, 2), 13L),
            new ParsedBooking("h-1", 1L, -4_532, LocalDate.of(2026, 2, 1), 12L)
        );

        ImportResult result = service.importBookings(parsed);

        assertEquals(3, result.total);
        assertEquals(2, result.inserted);
        assertEquals(1, result.duplicates);
        assertEquals(242_618, ledger.getAccount(1L).currentBalanceCents);
    }

    @Test
    public void bookingChanges_recalculateOldAndNewAccountBalances() {
        InMemoryLedger ledger = new InMemoryLedger();
        ledger.putAccount(new AccountState(1L, 250_000));
        ledger.putAccount(new AccountState(2L, 10_000));

        BookingService service = new BookingService(ledger);
        Long bookingId = service.create(new Booking(1L, -5_000, LocalDate.of(2026, 2, 2), 12L, "b-1"));

        assertEquals(245_000, ledger.getAccount(1L).currentBalanceCents);

        service.update(bookingId, new Booking(2L, -7_500, LocalDate.of(2026, 2, 2), 12L, "b-1-updated"));

        assertEquals(250_000, ledger.getAccount(1L).currentBalanceCents);
        assertEquals(2_500, ledger.getAccount(2L).currentBalanceCents);

        assertFalse(service.isDuplicate(""));
        assertTrue(service.isDuplicate("b-1-updated"));
    }

    static final class ImportService {
        private final BookingService bookingService;

        ImportService(InMemoryLedger ledger) {
            this.bookingService = new BookingService(ledger);
        }

        ImportResult importBookings(List<ParsedBooking> parsedBookings) {
            int inserted = 0;
            int duplicates = 0;

            for (ParsedBooking p : parsedBookings) {
                if (bookingService.isDuplicate(p.importHash)) {
                    duplicates++;
                    continue;
                }
                bookingService.create(new Booking(p.accountId, p.amountCents, p.date, p.categoryId, p.importHash));
                inserted++;
            }

            return new ImportResult(parsedBookings.size(), inserted, duplicates);
        }
    }

    static final class BookingService {
        private final InMemoryLedger ledger;

        BookingService(InMemoryLedger ledger) {
            this.ledger = ledger;
        }

        Long create(Booking booking) {
            Long id = ledger.saveBooking(booking);
            recalculateAccountBalance(booking.accountId);
            return id;
        }

        void update(Long id, Booking updated) {
            Booking old = ledger.getBooking(id);
            ledger.saveBookingWithId(id, updated);

            recalculateAccountBalance(updated.accountId);
            if (old != null && !Objects.equals(old.accountId, updated.accountId)) {
                recalculateAccountBalance(old.accountId);
            }
        }

        boolean isDuplicate(String importHash) {
            if (importHash == null || importHash.isEmpty()) return false;
            return ledger.allBookings().stream()
                .anyMatch(tx -> importHash.equals(tx.importHash));
        }

        private void recalculateAccountBalance(Long accountId) {
            AccountState acc = ledger.getAccount(accountId);
            if (acc == null) return;

            int balance = acc.initialBalanceCents;
            for (Booking b : ledger.allBookings()) {
                if (Objects.equals(accountId, b.accountId)) {
                    balance += b.amountCents;
                }
            }
            acc.currentBalanceCents = balance;
        }
    }

    static final class ParsedBooking {
        final String importHash;
        final Long accountId;
        final int amountCents;
        final LocalDate date;
        final Long categoryId;

        ParsedBooking(String importHash, Long accountId, int amountCents, LocalDate date, Long categoryId) {
            this.importHash = importHash;
            this.accountId = accountId;
            this.amountCents = amountCents;
            this.date = date;
            this.categoryId = categoryId;
        }
    }

    static final class Booking {
        final Long accountId;
        final int amountCents;
        final LocalDate date;
        final Long categoryId;
        final String importHash;

        Booking(Long accountId, int amountCents, LocalDate date, Long categoryId, String importHash) {
            this.accountId = accountId;
            this.amountCents = amountCents;
            this.date = date;
            this.categoryId = categoryId;
            this.importHash = importHash;
        }
    }

    static final class AccountState {
        final Long id;
        final int initialBalanceCents;
        int currentBalanceCents;

        AccountState(Long id, int initialBalanceCents) {
            this.id = id;
            this.initialBalanceCents = initialBalanceCents;
            this.currentBalanceCents = initialBalanceCents;
        }
    }

    static final class InMemoryLedger {
        private final Map<Long, AccountState> accounts = new HashMap<>();
        private final Map<Long, Booking> bookings = new HashMap<>();
        private long bookingSeq = 1;

        void putAccount(AccountState account) {
            accounts.put(account.id, account);
        }

        AccountState getAccount(Long id) {
            return accounts.get(id);
        }

        Long saveBooking(Booking booking) {
            Long id = bookingSeq++;
            bookings.put(id, booking);
            return id;
        }

        void saveBookingWithId(Long id, Booking booking) {
            bookings.put(id, booking);
        }

        Booking getBooking(Long id) {
            return bookings.get(id);
        }

        List<Booking> allBookings() {
            return new ArrayList<>(bookings.values());
        }
    }

    static final class ImportResult {
        final int total;
        final int inserted;
        final int duplicates;

        ImportResult(int total, int inserted, int duplicates) {
            this.total = total;
            this.inserted = inserted;
            this.duplicates = duplicates;
        }
    }
}
