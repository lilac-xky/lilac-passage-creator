package com.lilac.service;

import com.lilac.domain.dto.image.ImageData;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 腾讯云对象存储服务
 */
@Service
@Slf4j
public class CosService {

    private final COSClient cosClient;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final String bucket;
    private final String host;

    public CosService(@Value("${cos.client.secretId}") String secretId,
                      @Value("${cos.client.secretKey}") String secretKey,
                      @Value("${cos.client.region}") String region,
                      @Value("${cos.client.bucket}") String bucket,
                      @Value("${cos.client.host}") String host) {
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        this.cosClient = new COSClient(credentials, new ClientConfig(new Region(region)));
        this.bucket = bucket;
        this.host = host;
    }

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

    /**
     * 上传 ImageData 到 COS（统一入口）
     * 根据数据类型自动选择上传方式
     *
     * @param imageData 图片数据对象
     * @param folder    文件夹
     * @return COS 图片 URL，上传失败返回 null
     */
    public String uploadImageData(ImageData imageData, String folder) {
        if (imageData == null || !imageData.isValid()) {
            log.warn("ImageData 无效，无法上传");
            return null;
        }

        try {
            return switch (imageData.getDataType()) {
                case BYTES -> uploadBytes(imageData.getBytes(), imageData.getMimeType(), folder);
                case URL -> uploadFromUrl(imageData.getUrl(), folder);
                case DATA_URL -> uploadFromDataUrl(imageData, folder);
            };
        } catch (Exception e) {
            log.error("上传 ImageData 到 COS 失败, dataType={}", imageData.getDataType(), e);
            return null;
        }
    }

    /**
     * 上传字节数据到 COS
     */
    public String uploadBytes(byte[] bytes, String mimeType, String folder) {
        if (bytes == null || bytes.length == 0) {
            log.warn("字节数据为空，无法上传");
            return null;
        }

        try {
            // 生成文件名
            String extension = getExtensionFromMimeType(mimeType);
            String fileName = folder + "/" + UUID.randomUUID() + extension;

            // 上传到 COS
            try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(bytes.length);
                metadata.setContentType(mimeType != null ? mimeType : "image/png");

                PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, fileName, inputStream, metadata);

                cosClient.putObject(putObjectRequest);

                String cosUrl = buildCosUrl(fileName);
                log.info("字节数据上传成功, size={} bytes, url={}", bytes.length, cosUrl);
                return cosUrl;
            }
        } catch (Exception e) {
            log.error("上传字节数据到 COS 失败", e);
            return null;
        }
    }

    /**
     * 从外部 URL 下载并上传到 COS
     */
    public String uploadFromUrl(String imageUrl, String folder) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            log.warn("图片 URL 为空，无法上传");
            return null;
        }

        try {
            // 下载图片
            Request request = new Request.Builder().url(imageUrl).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("下载图片失败: {}, code={}", imageUrl, response.code());
                    return null;
                }

                byte[] imageBytes = response.body().bytes();
                String contentType = response.header("Content-Type", "image/jpeg");

                // 上传字节数据
                return uploadBytes(imageBytes, contentType, folder);
            }
        } catch (IOException e) {
            log.error("从 URL 上传图片到 COS 失败: {}", imageUrl, e);
            return null;
        }
    }

    /**
     * 从 base64 data URL 解码并上传到 COS
     */
    public String uploadFromDataUrl(ImageData imageData, String folder) {
        byte[] bytes = imageData.getImageBytes();
        if (bytes == null || bytes.length == 0) {
            log.warn("解码 data URL 失败，无法上传");
            return null;
        }
        return uploadBytes(bytes, imageData.getMimeType(), folder);
    }

    private String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) {
            return ".png";
        }
        return switch (mimeType.split(";", 2)[0].trim().toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            default -> ".png";
        };
    }

    private String buildCosUrl(String fileName) {
        return host.replaceAll("/+$", "") + "/" + fileName;
    }
}
