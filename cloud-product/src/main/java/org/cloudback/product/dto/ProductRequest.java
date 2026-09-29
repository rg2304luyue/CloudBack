package org.cloudback.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotNull(message = "分类ID不能为空")
        Long categoryId,
        @NotBlank(message = "商品名称不能为空")
        @Size(max = 100, message = "商品名称不能超过100个字符")
        String name,
        String description,
        @NotNull(message = "价格不能为空")
        @DecimalMin(value = "0.01", message = "价格不能低于0.01")
        @Digits(integer = 8, fraction = 2, message = "价格格式不正确，最多8位整数2位小数")
        BigDecimal price,
        @NotNull(message = "库存不能为空")
        @Min(value = 0, message = "库存不能为负数")
        @Max(value = 999999, message = "库存不能超过999999")
        Integer stock,
        @Size(max = 512, message = "主图地址过长")
        String mainImage,
        @Size(max = 512, message = "图片列表过长")
        String images
) {}
