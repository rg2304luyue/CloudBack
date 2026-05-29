package org.cloudback.product.dto;

public record CategoryRequest(
        String name,
        Long parentId,
        Integer sortOrder
) {}

