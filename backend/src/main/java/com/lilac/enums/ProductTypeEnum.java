package com.lilac.enums;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 产品类型枚举
 */
@Getter
public enum ProductTypeEnum {

    VIP_PERMANENT("VIP_PERMANENT", "Lilac 永久 VIP", new BigDecimal("199"));

    private final String value;
    private final String description;
    private final BigDecimal price;

    ProductTypeEnum(String value, String description, BigDecimal price) {
        this.value = value;
        this.description = description;
        this.price = price;
    }
}