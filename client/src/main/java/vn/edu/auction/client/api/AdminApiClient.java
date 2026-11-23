package vn.edu.auction.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import vn.edu.auction.client.api.AuthApiClient.ApiException;
import vn.edu.auction.shared.dto.ExtraDto.*;
import vn.edu.auction.shared.dto.ItemResponse;
import vn.edu.auction.shared.enums.UserStatus;

public class AdminApiClient extends BaseApiClient {
    public List<AdminUserResponse> users() throws ApiException {
        return call("GET", "/api/admin/users", null, new TypeReference<List<AdminUserResponse>>() {});
    }
    public void setUserStatus(Long id, UserStatus s) throws ApiException {
        call("PATCH", "/api/admin/users/" + id + "/status", new StatusRequest(s), null);
    }
    public void deleteUser(Long id) throws ApiException { call("DELETE", "/api/admin/users/" + id, null, null); }

    public List<ItemResponse> allItems() throws ApiException {
        return call("GET", "/api/admin/items", null, new TypeReference<List<ItemResponse>>() {});
    }

    public List<ItemResponse> pendingItems() throws ApiException {
        return call("GET", "/api/admin/items/pending", null, new TypeReference<List<ItemResponse>>() {});
    }

    public void approveItem(Long id) throws ApiException {
        approveItem(id, null);
    }

    public void approveItem(Long id, String feedback) throws ApiException {
        ReviewFeedbackRequest body = (feedback != null && !feedback.isBlank()) ? new ReviewFeedbackRequest(feedback.trim()) : null;
        call("POST", "/api/admin/items/" + id + "/approve", body, null);
    }

    public void rejectItem(Long id) throws ApiException {
        rejectItem(id, null);
    }

    public void rejectItem(Long id, String feedback) throws ApiException {
        ReviewFeedbackRequest body = (feedback != null && !feedback.isBlank()) ? new ReviewFeedbackRequest(feedback.trim()) : null;
        call("POST", "/api/admin/items/" + id + "/reject", body, null);
    }

    public void deleteItem(Long id) throws ApiException {
        call("DELETE", "/api/admin/items/" + id, null, null);
    }

    public List<AdminAuctionResponse> auctions() throws ApiException {
        return call("GET", "/api/admin/auctions", null, new TypeReference<List<AdminAuctionResponse>>() {});
    }
    public void stopAuction(Long id) throws ApiException { call("POST", "/api/admin/auctions/" + id + "/stop", null, null); }
    public void setHidden(Long id, boolean hidden) throws ApiException {
        call("PUT", "/api/admin/auctions/" + id + "/hidden?value=" + hidden, null, null);
    }

    /** Gọi POST /api/admin/users/{id}/promote để nâng tài khoản lên ADMIN. */
    public void promoteToAdmin(Long id) throws ApiException {
        call("POST", "/api/admin/users/" + id + "/promote", null, null);
    }
}
