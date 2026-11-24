package vn.edu.auction.shared.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import vn.edu.auction.shared.enums.AuctionStatus;

class AuctionTest {
    private static final LocalDateTime END = LocalDateTime.of(2026, 1, 1, 20, 0, 0);

    private Auction newAuction() {
        return new Auction(7L, 1L, new BigDecimal("100"), END.minusHours(1), END);
    }

    @Test
    void acceptsHigherBidAndRejectsLowerOneWithoutChangingState() {
        Auction a = newAuction();
        a.placeBid(11L, new BigDecimal("150"), END.minusMinutes(5));
        assertEquals(AuctionStatus.RUNNING, a.getStatus());
        assertThrows(BidRejectedException.class,
                () -> a.placeBid(12L, new BigDecimal("150"), END.minusMinutes(4)));
        assertEquals(11L, a.getHighestBidderId());
        assertEquals(0, new BigDecimal("150").compareTo(a.getCurrentPrice()));
    }

    @Test
    void extendsOnlyInsideTheLastTenSeconds() {
        Auction a = newAuction();
        a.placeBid(11L, new BigDecimal("150"), END.minusSeconds(11));
        assertEquals(END, a.getEndTime());
        a.placeBid(12L, new BigDecimal("200"), END.minusSeconds(10));
        assertEquals(END.plusSeconds(60), a.getEndTime());
    }

    @Test
    void rejectsBidAfterEndTime() {
        Auction a = newAuction();
        assertThrows(BidRejectedException.class,
                () -> a.placeBid(11L, new BigDecimal("150"), END));
    }

    @Test
    void followsStatusFlowToPaid() {
        Auction a = newAuction();
        a.placeBid(11L, new BigDecimal("150"), END.minusMinutes(1));
        assertThrows(IllegalStateException.class, () -> a.finish(END.minusSeconds(1)));
        a.finish(END);
        assertEquals(AuctionStatus.FINISHED, a.getStatus());
        a.markPaid();
        assertEquals(AuctionStatus.PAID, a.getStatus());
        assertThrows(IllegalStateException.class, a::cancel);
    }

    @Test
    void auctionWithoutBidsHasNoWinnerAndCannotBePaid() {
        Auction a = newAuction();
        a.finish(END);
        assertFalse(a.hasWinner());
        assertThrows(IllegalStateException.class, a::markPaid);
    }
}