package vn.edu.auction.server.service;

import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.auction.server.entity.ItemEntity;
import vn.edu.auction.server.repository.*;
import vn.edu.auction.shared.dto.*;
import vn.edu.auction.shared.enums.*;

@Service
@Transactional(readOnly = true)
public class ItemService {
    private final ItemRepository items;
    private final AuctionRepository auctions;
    private final AuctionUserService users;

    public ItemService(ItemRepository items, AuctionRepository auctions, AuctionUserService users) {
        this.items = items;
        this.auctions = auctions;
        this.users = users;
    }

    /** Danh sách cho người xem cụ thể: sản phẩm chờ duyệt (DRAFT) chỉ chủ sở hữu hoặc ADMIN mới thấy. */
    public List<ItemResponse> getAllItems(Long viewerId) {
        boolean isAdmin = users.isAdmin(viewerId);
        return items.findAll().stream()
                .filter(i -> visibleTo(i, viewerId, isAdmin))
                .map(this::response).toList();
    }

    public ItemResponse getItemById(Long id, Long viewerId) {
        ItemEntity item = items.findById(id).orElse(null);
        AuctionRules.require(item != null && visibleTo(item, viewerId, users.isAdmin(viewerId)),
                HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm.");
        return response(item);
    }

    @Transactional
    public ItemResponse createItem(ItemRequest request, Long sellerId) {
        users.requireRole(sellerId, UserRole.SELLER);
        ItemEntity item = new ItemEntity();
        item.setSellerId(sellerId);
        item.setStatus(ItemStatus.DRAFT);
        apply(item, request);
        return response(items.save(item));
    }

    @Transactional
    public ItemResponse updateItem(Long id, ItemRequest request, Long sellerId) {
        ItemEntity item = editableItem(id, sellerId);
        apply(item, request);
        return response(item);
    }

    @Transactional
    public void deleteItem(Long id, Long sellerId) {
        editableItem(id, sellerId).setStatus(ItemStatus.REMOVED);
    }

    private ItemEntity editableItem(Long id, Long sellerId) {
        users.requireRole(sellerId, UserRole.SELLER);
        ItemEntity item = items.findByIdForUpdate(id).orElse(null);
        AuctionRules.require(item != null && item.getStatus() != ItemStatus.REMOVED, HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm.");
        AuctionRules.require(item.getSellerId().equals(sellerId), HttpStatus.FORBIDDEN, "Bạn không sở hữu sản phẩm này.");
        AuctionRules.require(!auctions.existsByItemId(id), HttpStatus.CONFLICT, "Sản phẩm đã có phiên đấu giá, không thể sửa hoặc xóa.");
        return item;
    }

    private boolean visibleTo(ItemEntity item, Long viewerId, boolean isAdmin) {
        return item.getStatus() != ItemStatus.REMOVED
                && (item.getStatus() != ItemStatus.DRAFT || isAdmin || item.getSellerId().equals(viewerId));
    }

    private void apply(ItemEntity item, ItemRequest request) {
        AuctionRules.require(request != null && request.getName() != null && !request.getName().isBlank(),
                HttpStatus.BAD_REQUEST, "Tên sản phẩm không được để trống.");
        String name = request.getName().trim();
        String description = request.getDescription() == null ? "" : request.getDescription().trim();
        AuctionRules.require(name.length() <= 255 && description.length() <= 4000,
                HttpStatus.BAD_REQUEST, "Tên hoặc mô tả sản phẩm quá dài.");
        String image = request.getImage() == null ? item.getImage() : request.getImage();
        if (image != null && !image.isEmpty()) {
            AuctionRules.require(vn.edu.auction.shared.dto.ProductImages.valid(image),
                    HttpStatus.BAD_REQUEST, "Ảnh sản phẩm không hợp lệ.");
        }
        var price = AuctionRules.money(request.getStartingPrice());
        if (item.getStatus() == ItemStatus.ACTIVE && (!name.equals(item.getName())
                || !description.equals(Objects.toString(item.getDescription(), ""))
                || !Objects.equals(image, item.getImage())
                || price.compareTo(item.getStartingPrice()) != 0)) {
            item.setStatus(ItemStatus.DRAFT);
        }
        item.setImage(image);
        item.setName(name);
        item.setDescription(description);
        item.setStartingPrice(price);
    }

    private ItemResponse response(ItemEntity item) {
        ItemResponse resp = new ItemResponse(item.getId(), item.getSellerId(), item.getName(), item.getDescription(), item.getStartingPrice(), item.getStatus());
        resp.setImage(item.getImage());
        auctions.findByItemId(item.getId()).ifPresent(a -> resp.setAuctionStatus(a.getStatus()));
        return resp;
    }
}
