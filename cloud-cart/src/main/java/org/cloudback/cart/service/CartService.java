package org.cloudback.cart.service;

import org.cloudback.cart.dto.CartItem;
import org.cloudback.common.result.R;
import java.util.List;

/**
 * 购物车服务接口，所有数据存储在 Redis Hash 中。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
public interface CartService {

    /** 添加商品到购物车，已存在则累加数量 */
    R<String> addItem(Long userId, Long productId, Integer quantity);

    /** 更新购物车中商品数量 */
    R<String> updateQuantity(Long userId, Long productId, Integer quantity);

    /** 移除购物车中的商品 */
    R<String> removeItem(Long userId, Long productId);

    /** 勾选/取消勾选商品 */
    R<String> checkItem(Long userId, Long productId, Boolean checked);

    /** 批量勾选/取消勾选所有商品 */
    R<String> checkAllItems(Long userId, Boolean checked);

    /** 清空购物车 */
    R<String> clearCart(Long userId);

    /** 获取购物车全部商品列表 */
    R<List<CartItem>> getCartList(Long userId);

    /** 获取已勾选的商品列表（下单时使用） */
    R<List<CartItem>> getCheckedItems(Long userId);
}
