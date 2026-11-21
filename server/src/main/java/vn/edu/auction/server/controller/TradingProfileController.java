package vn.edu.auction.server.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.auction.server.service.TradingProfileService;
import vn.edu.auction.shared.dto.TradingProfiles;

@RestController
public class TradingProfileController {
    private final TradingProfileService profiles;
    public TradingProfileController(TradingProfileService profiles) { this.profiles = profiles; }

    @GetMapping("/api/me/trading-profiles")
    public TradingProfiles mine(Authentication auth) {
        return profiles.get(AuctionPrincipal.userId(auth));
    }

    @PutMapping("/api/me/trading-profiles")
    public TradingProfiles save(Authentication auth, @RequestBody TradingProfiles request) {
        return profiles.save(AuctionPrincipal.userId(auth), request);
    }

    @GetMapping("/api/users/{id}/profile/{side:buyer|seller}")
    public TradingProfiles.Card publicProfile(@PathVariable("id") Long id, @PathVariable("side") String side) {
        return profiles.get(id).publicCard(side.equals("seller"));
    }
}
