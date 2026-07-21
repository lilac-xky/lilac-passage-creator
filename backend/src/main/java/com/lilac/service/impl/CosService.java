package com.lilac.service.impl;

import org.springframework.stereotype.Service;

/**
 * 腾讯云对象存储服务
 */
@Service
public class CosService {
    /**
     * 使用图片源站直链，不上传到 COS。
     *
     * @param imageUrl 图片源站地址
     * @return 可直接使用的图片地址；地址为空时返回 null
     */
    public String useDirectUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return imageUrl.trim();
    }
}
