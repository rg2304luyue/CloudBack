package org.cloudback.cart.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.cart.dto.CartItem;
import org.cloudback.cart.service.CartService;
import org.cloudback.common.result.R;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @PostMapping("/add")
    public R<String> addItem(@RequestHeader("X-User-Id") Long userId,
                             @RequestParam Long productId,
                             @RequestParam(defaultValue = "1") Integer quantity) {
        return cartService.addItem(userId, productId, quantity);
    }

    @PutMapping("/quantity")
    public R<String> updateQuantity(@RequestHeader("X-User-Id") Long userId,
                                    @RequestParam Long productId,
                                    @RequestParam Integer quantity) {
        return cartService.updateQuantity(userId, productId, quantity);
    }

    @PutMapping("/check")
    public R<String> checkItem(@RequestHeader("X-User-Id") Long userId,
                               @RequestParam Long productId,
                               @RequestParam Boolean checked) {
        return cartService.checkItem(userId, productId, checked);
    }

    @DeleteMapping("/{productId}")
    public R<String> removeItem(@RequestHeader("X-User-Id") Long userId,
                                @PathVariable Long productId) {
        return cartService.removeItem(userId, productId);
    }

    @DeleteMapping("/clear")
    public R<String> clearCart(@RequestHeader("X-User-Id") Long userId) {
        return cartService.clearCart(userId);
    }

    @GetMapping("/list")
    public R<List<CartItem>> getCartList(@RequestHeader("X-User-Id") Long userId) {
        return cartService.getCartList(userId);
    }

    @GetMapping("/checked")
    public R<List<CartItem>> getCheckedItems(@RequestHeader("X-User-Id") Long userId) {
        return cartService.getCheckedItems(userId);
    }
}
