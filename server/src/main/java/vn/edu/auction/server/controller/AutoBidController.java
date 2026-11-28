package vn.edu.auction.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.auction.server.service.AutoBidService;
import vn.edu.auction.shared.dto.AutoBidRequest;
import vn.edu.auction.shared.dto.AutoBidResponse;

@RestController
@RequestMapping("/api/auctions/{id}/auto-bid")
public class AutoBidController {
    private final AutoBidService autoBids;

    public AutoBidController(AutoBidService autoBids) { this.autoBids = autoBids; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AutoBidResponse register(@PathVariable("id") Long id, @RequestBody AutoBidRequest request, Authentication auth) {
        return autoBids.register(id, AuctionPrincipal.userId(auth), request);
    }

    @GetMapping
    public AutoBidResponse mine(@PathVariable("id") Long id, Authentication auth) {
        return autoBids.getMine(id, AuctionPrincipal.userId(auth));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable("id") Long id, Authentication auth) {
        autoBids.cancel(id, AuctionPrincipal.userId(auth));
    }
}