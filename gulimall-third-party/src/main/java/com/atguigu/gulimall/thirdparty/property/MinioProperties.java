package com.atguigu.gulimall.thirdparty.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @ClassName: MinioProperties
 * @Package: com.atguigu.gulimall.thirdparty.property
 * @Description:
 * @Author: 刘家旭
 * @Create: 2026/9/2 - 21:55
 * @Version: v1.0
 */
@Data
@ConfigurationProperties(prefix="gulimall.minio") //读取节点
public class MinioProperties {

    private String endpointUrl;
    private String accessKey;
    private String secreKey;
    private String bucketName;

}