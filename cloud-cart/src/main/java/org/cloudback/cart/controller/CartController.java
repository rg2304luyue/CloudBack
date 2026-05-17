package org.cloudback.cart.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.cart.dto.CartItem;
import org.cloudback.cart.service.CartService;
import org.cloudback.common.result.R;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 购物车控制器，提供购物车的增删改查接口。
 * 购物车数据存储在 Redis，用户身份通过 X-User-Id 请求头获取。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** 添加商品到购物车 */
    @PostMapping("/add")
    public R<String> addItem(@RequestHeader("X-User-Id") Long userId,
                             @RequestParam Long productId,
                             @RequestParam(defaultValue = "1") Integer quantity) {
        return cartService.addItem(userId, productId, quantity);
    }

    /** 更新商品数量 */
    @PutMapping("/quantity")
    public R<String> updateQuantity(@RequestHeader("X-User-Id") Long userId,
                                    @RequestParam Long productId,
                                    @RequestParam Integer quantity) {
        return cartService.updateQuantity(userId, productId, quantity);
    }

    /** 勾选/取消勾选 */
    @PutMapping("/check")
    public R<String> checkItem(@RequestHeader("X-User-Id") Long userId,
                               @RequestParam Long productId,
                               @RequestParam Boolean checked) {
        return cartService.checkItem(userId, productId, checked);
    }

    /** 删除单个商品 */
    @DeleteMapping("/{productId}")
    public R<String> removeItem(@RequestHeader("X-User-Id") Long userId,
                                @PathVariable Long productId) {
        return cartService.removeItem(userId, productId);
    }

    /** 清空购物车 */
    @DeleteMapping("/clear")
    public R<String> clearCart(@RequestHeader("X-User-Id") Long userId) {
        return cartService.clearCart(userId);
    }

    /** 查看购物车全部商品 */
    @GetMapping("/list")
    public R<List<CartItem>> getCartList(@RequestHeader("X-User-Id") Long userId) {
        return cartService.getCartList(userId);
    }

    /** 获取已勾选商品（下单时用） */
    @GetMapping("/checked")
    public R<List<CartItem>> getCheckedItems(@RequestHeader("X-User-Id") Long userId) {
        return cartService.getCheckedItems(userId);
    }
}
