package org.cloudback.product.controller;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.result.R;
import org.cloudback.product.model.entity.Category;
import org.cloudback.product.model.entity.Product;
import org.cloudback.product.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 商品服务控制器，提供分类管理和商品 CRUD 接口。
 * 库存扣减接口供订单服务 Feign 内部调用。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.endpoint}")
    private String endpoint;

    /** 获取分类树 */
    @GetMapping("/category")
    public R<List<Category>> getCategoryTree() {
        return productService.getCategoryTree();
    }

    /** 添加分类 */
    @PostMapping("/category")
    public R<String> addCategory(@RequestHeader("X-User-Id") Long userId,
                                 @RequestHeader("X-User-Role") String role,
                                 @RequestBody Category category) {
        return productService.addCategory(userId, role, category);
    }

    /** 修改分类 */
    @PutMapping("/category")
    public R<String> updateCategory(@RequestHeader("X-User-Id") Long userId,
                                    @RequestHeader("X-User-Role") String role,
                                    @RequestBody Category category) {
        return productService.updateCategory(userId, role, category);
    }

    /** 删除分类 */
    @DeleteMapping("/category/{id}")
    public R<String> deleteCategory(@RequestHeader("X-User-Id") Long userId,
                                    @RequestHeader("X-User-Role") String role,
                                    @PathVariable Long id) {
        return productService.deleteCategory(userId, role, id);
    }

    /** 获取商品详情 */
    @GetMapping("/detail/{id}")
    public R<Product> getProductDetail(@PathVariable Long id) {
        return productService.getProductDetail(id);
    }

    /** 获取热门商品 Top8，按浏览量排序 */
    @GetMapping("/hot")
    public R<List<Product>> getHotProducts() {
        return productService.getHotProducts();
    }

    /** 分页搜索商品列表 */
    @GetMapping("/list")
    public R<List<Product>> getProductList(@RequestParam(required = false) Long categoryId,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) String keyword) {
        return productService.getProductList(categoryId, page, size, keyword);
    }

    /** 卖家查看自己的商品 */
    @GetMapping("/my-list")
    public R<List<Product>> getMyProducts(@RequestHeader("X-User-Id") Long userId,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "20") Integer size) {
        return productService.getMyProducts(userId, page, size);
    }

    /** 添加商品 */
    @PostMapping
    public R<String> addProduct(@RequestHeader("X-User-Id") Long userId,
                                @RequestHeader("X-User-Role") String role,
                                @RequestBody Product product) {
        return productService.addProduct(userId, role, product);
    }

    /** 修改商品 */
    @PutMapping
    public R<String> updateProduct(@RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader("X-User-Role") String role,
                                   @RequestBody Product product) {
        return productService.updateProduct(userId, role, product);
    }

    /** 删除商品 */
    @DeleteMapping("/{id}")
    public R<String> deleteProduct(@RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader("X-User-Role") String role,
                                   @PathVariable Long id) {
        return productService.deleteProduct(userId, role, id);
    }

    /** 扣减库存（供订单服务 Feign 内部调用） */
    @PutMapping("/stock/deduct/{id}")
    public R<String> deductStock(@PathVariable Long id,
                                 @RequestParam Integer quantity) {
        return productService.deductStock(id, quantity);
    }

    /** 管理员：获取待审核商品 */
    @GetMapping("/admin/pending")
    public R<List<Product>> getPendingProducts(@RequestParam(defaultValue = "1") Long page,
                                               @RequestParam(defaultValue = "20") Long size) {
        return productService.getPendingProducts(page, size);
    }

    /** 管理员：审核商品 */
    @PutMapping("/admin/review/{id}")
    public R<String> reviewProduct(@PathVariable Long id,
                                   @RequestParam boolean approved) {
        return productService.reviewProduct(id, approved);
    }

    /** 上传图片到 MinIO，返回可访问的 URL */
    @PostMapping("/upload")
    public R<String> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return R.fail("请选择文件");
        }
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String objectName = "product/" + UUID.randomUUID() + ext;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            String url = "http://" + endpoint + "/" + bucket + "/" + objectName;
            return R.ok(url);
        } catch (Exception e) {
            log.error("上传文件到 MinIO 失败", e);
            return R.fail("上传失败，请重试");
        }
    }
}
