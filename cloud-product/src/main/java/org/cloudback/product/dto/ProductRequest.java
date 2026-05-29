package org.cloudback.product.dto;

import java.math.BigDecimal;

public record ProductRequest(
        Long categoryId,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String mainImage,
        String images
) {}
