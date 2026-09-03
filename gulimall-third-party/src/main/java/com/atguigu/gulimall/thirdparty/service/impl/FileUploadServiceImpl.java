package com.atguigu.gulimall.thirdparty.service.impl;

import com.atguigu.gulimall.thirdparty.property.MinioProperties;
import com.atguigu.gulimall.thirdparty.service.FileUploadService;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @ClassName: FileUploadServiceImpl
 * @Package: com.atguigu.gulimall.thirdparty.service.impl
 * @Description: MinIO 前端直传实现：签发 presigned PUT/GET URL，文件由前端直接上传 MinIO，后端不碰文件流
 * @Author: 刘家旭
 * @Create: 2026/9/2 - 21:54
 * @Version: v1.0
 */
@Service
public class FileUploadServiceImpl implements FileUploadService {

    /** PUT 直传签名 URL 有效期（秒），默认 10 分钟 */
    private static final int PUT_EXPIRY_SECONDS = 60 * 10;
    /** GET 访问签名 URL 有效期（秒），默认 7 天，供前端展示 */
    private static final int GET_EXPIRY_SECONDS = 60 * 60 * 24 * 7;

    private final MinioProperties minioProperties;
    private final MinioClient minioClient;

    public FileUploadServiceImpl(MinioProperties minioProperties, MinioClient minioClient) {
        this.minioProperties = minioProperties;
        this.minioClient = minioClient;
    }

    @Override
    public Map<String, String> getUploadUrl(String originalFilename) throws Exception {
        // 判断桶是否存在，不存在则创建
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucketName()).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucketName()).build());
        }

        // 对象 key：日期目录/uuid+原文件名（如 2026-09-02/443e...a901.png）
        String dateDir = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String objectName = dateDir + "/" + uuid + originalFilename;

        // PUT 直传签名 URL（前端拿到后直接 PUT 文件二进制到该 URL）
        String putUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(minioProperties.getBucketName())
                .object(objectName)
                .expiry(PUT_EXPIRY_SECONDS)
                .build());

        // GET 可访问签名 URL（前端存它用于 <img> 展示，私有桶下带签名才能访问）
        String getUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(minioProperties.getBucketName())
                .object(objectName)
                .expiry(GET_EXPIRY_SECONDS)
                .build());

        Map<String, String> result = new LinkedHashMap<>();
        result.put("putUrl", putUrl);
        result.put("getUrl", getUrl);
        return result;
    }

}
