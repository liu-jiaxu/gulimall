package com.atguigu.gulimall.thirdparty;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.ErrorResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

//@SpringBootTest
class GulimallThirdPartyApplicationTests {

    @Test
    public void minIO() throws InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException, io.minio.errors.ServerException, io.minio.errors.ErrorResponseException {
        // 创建一个Minio的客户端对象
        MinioClient minioClient = MinioClient.builder()
                .endpoint("http://192.168.10.200:19000")
                .credentials("admin", "zgh2960425")
                .build();

        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket("gulimall").build());

        // 如果不存在，那么此时就创建一个新的桶
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("gulimall").build());
        } else {  // 如果存在打印信息
            System.out.println("Bucket 'gulimall' already exists.");
        }

        // 从 classpath 加载测试资源（src/test/resources/kunkun.jpg 编译后进 target/test-classes），
        // 不依赖当前工作目录
        InputStream is = GulimallThirdPartyApplicationTests.class.getClassLoader().getResourceAsStream("kunkun.jpg");
        Assertions.assertNotNull(is);
        PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                .bucket("gulimall")
                .stream(is, is.available(), -1)
                .object("kunkun.jpg")
                .build();
        minioClient.putObject(putObjectArgs) ;

        // 构建fileUrl
        String fileUrl = "http://192.168.10.200:19000/gulimall/kunkun.jpg" ;
        System.out.println(fileUrl);
    }

}
