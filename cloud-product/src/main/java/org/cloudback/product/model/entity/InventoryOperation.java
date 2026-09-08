package org.cloudback.product.model.entity;

import lombok.Data;

/** Durable state of one order's inventory operation for one product. */
@Data
public class InventoryOperation {
    private String orderNo;
    private Long productId;
    private Integer quantity;
    private String state;
}
