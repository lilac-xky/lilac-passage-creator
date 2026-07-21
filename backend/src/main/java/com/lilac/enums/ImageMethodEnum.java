package com.lilac.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图片检索方式枚举
 */
@Getter
public enum ImageMethodEnum {

    PICSUM("Picsum"),
    PEXELS("Pexels"),
    UNSPLASH("Unsplash");

    private final String value;

    ImageMethodEnum(String value) {
        this.value = value;
    }

    /**
     * 根据值获取枚举项
     */
    public static ImageMethodEnum getByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (ImageMethodEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 获取所有枚举值列表
     *
     * @return 枚举值列表
     */
    public static List<String> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }
}
