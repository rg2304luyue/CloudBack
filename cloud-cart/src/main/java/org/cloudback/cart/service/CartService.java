package org.cloudback.cart.service;

import org.cloudback.cart.dto.CartItem;
import org.cloudback.common.result.R;
import java.util.List;

public interface CartService {
    R<String> addItem(Long userId, Long productId, Integer quantity);

    R<String> updateQuantity(Long userId, Long productId, Integer quantity);

    R<String> removeItem(Long userId, Long productId);

    R<String> checkItem(Long userId, Long productId, Boolean checked);

    R<String> clearCart(Long userId);

    R<List<CartItem>> getCartList(Long userId);

    R<List<CartItem>> getCheckedItems(Long userId);
}
