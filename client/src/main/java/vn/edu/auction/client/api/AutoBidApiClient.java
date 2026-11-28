package vn.edu.auction.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import vn.edu.auction.client.api.AuthApiClient.ApiException;
import vn.edu.auction.shared.dto.AutoBidRequest;
import vn.edu.auction.shared.dto.AutoBidResponse;

public class AutoBidApiClient extends BaseApiClient {
    private static String path(Long auctionId) { return "/api/auctions/" + auctionId + "/auto-bid"; }

    public AutoBidResponse register(Long auctionId, AutoBidRequest request) throws ApiException {
        return call("POST", path(auctionId), request, new TypeReference<AutoBidResponse>() {});
    }

    /** Trả về null nếu chưa bật (server trả body rỗng). */
    public AutoBidResponse getMine(Long auctionId) throws ApiException {
        return call("GET", path(auctionId), null, new TypeReference<AutoBidResponse>() {});
    }

    public void cancel(Long auctionId) throws ApiException {
        call("DELETE", path(auctionId), null, (TypeReference<Void>) null);
    }
}