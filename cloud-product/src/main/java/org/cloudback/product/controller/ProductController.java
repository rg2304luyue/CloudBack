package org.cloudback.product.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.result.R;
import org.cloudback.common.service.FileService;
import org.cloudback.product.model.entity.Product;
import org.cloudback.product.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 商品服务控制器，提供分类管理和商品 CRUD 接口。
 * 库存扣减接口供订单服务 Feign 内部调用。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final FileService fileService;

    // ===== 公共查询 =====

    /** GET /products/{id} — 获取商品详情（含布隆过滤器拦截 + 两级缓存 + 浏览量递增） */
    @GetMapping("/{id}")
    public R<Product> getProductDetail(@PathVariable Long id) {
        return productService.getProductDetail(id);
    }

    /** GET /products/hot — 获取热门商品 Top8，按 Redis 浏览量 ZSET 排序 */
    @GetMapping("/hot")
    public R<List<Product>> getHotProducts() {
        return productService.getHotProducts();
    }

    /** GET /products — 分页查询商品列表，支持分类筛选、关键词搜索、价格/销量排序 */
    @GetMapping
    public R<List<Product>> getProductList(@RequestParam(required = false) Long categoryId,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String sortBy) {
        return productService.getProductList(categoryId, page, size, keyword, sortBy);
    }

    /** GET /products/search — Meilisearch 全文搜索，支持中文分词和拼写纠错 */
    @GetMapping("/search")
    public R<List<Product>> search(@RequestParam String keyword,
                                   @RequestParam(required = false) Long categoryId,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer size) {
        return productService.search(keyword, categoryId, page, size);
    }

    /** GET /products/suggest — 搜索自动补全建议，基于 Meilisearch 商品名前缀匹配 */
    @GetMapping("/suggest")
    public R<List<String>> suggest(@RequestParam String keyword,
                                   @RequestParam(defaultValue = "10") int limit) {
        return productService.suggest(keyword, limit);
    }

    // ===== Feign 内部接口（供订单服务调用） =====

    /** POST /products/{id}/stock/deduct — 原子扣减库存，WHERE stock>=quantity 防超卖 */
    @PostMapping("/{id}/stock/deduct")
    public R<String> deductStock(@PathVariable Long id,
                                 @RequestParam Integer quantity) {
        return productService.deductStock(id, quantity);
    }

    /** POST /products/{id}/stock/restore — 回滚库存（取消订单/支付超时时恢复） */
    @PostMapping("/{id}/stock/restore")
    public R<String> restoreStock(@PathVariable Long id,
                                  @RequestParam Integer quantity) {
        return productService.restoreStock(id, quantity);
    }

    /** GET /products/seller/{sellerId} — 按卖家 ID 获取商品列表（供订单服务查询卖家订单时使用） */
    @GetMapping("/seller/{sellerId}")
    public R<List<Product>> getProductsBySellerId(@PathVariable Long sellerId) {
        return productService.getProductsBySellerId(sellerId);
    }

    // ===== 图片上传 =====

    /** POST /products/upload — 上传商品图片到 MinIO，返回公开访问 URL */
    @PostMapping("/upload")
    public R<String> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String url = fileService.upload(file, "product");
            return R.ok(url);
        } catch (Exception e) {
            log.error("上传文件失败", e);
            return R.fail("上传失败，请重试");
        }
    }
}
