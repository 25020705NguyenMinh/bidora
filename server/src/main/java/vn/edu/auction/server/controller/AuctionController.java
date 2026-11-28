package vn.edu.auction.server.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.auction.server.service.AuctionService;
import vn.edu.auction.shared.dto.*;
import vn.edu.auction.server.service.AuctionPaymentService;
import vn.edu.auction.shared.dto.ExtraDto.WalletResponse;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {
    private final AuctionService auctions;
    private final AuctionPaymentService payments;

    public AuctionController(AuctionService auctions, AuctionPaymentService payments) {
        this.auctions = auctions;
        this.payments = payments;
    }

    @GetMapping
    public List<AuctionResponse> getAllAuctions() { return auctions.getAllAuctions(); }

    @GetMapping("/{id}")
    public AuctionResponse getAuctionById(@PathVariable("id") Long id) { return auctions.getAuctionById(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuctionResponse createAuction(@RequestBody AuctionRequest request, Authentication auth) {
        return auctions.createAuction(request, AuctionPrincipal.userId(auth));
    }

    @PostMapping("/{id}/pay")
    public WalletResponse pay(@PathVariable("id") Long id, Authentication auth) {
        return payments.pay(id, AuctionPrincipal.userId(auth));
    }
}
