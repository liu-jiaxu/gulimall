package com.atguigu.gulimall.thirdparty.controller;

import com.atguigu.common.utils.R;
import com.atguigu.gulimall.thirdparty.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @ClassName: FileUploadController
 * @Package: com.atguigu.gulimall.thirdparty.controller
 * @Description:
 * @Author: 刘家旭
 * @Create: 2026/9/2 - 21:52
 * @Version: v1.0
 */
@RestController
@RequestMapping("/admin/system/minio")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    /**
     * 获取 MinIO presigned URL（前端直传 MinIO，后端不碰文件流）
     * <p>
     * <a href="http://localhost:30000/admin/system/minio/uploadUrl?fileName=test.png">http://localhost:30000/admin/system/minio/uploadUrl?fileName=test.png</a>
     * @param fileName 原始文件名
     * @return uploadUrl —— PUT 直传地址（短时 10 分钟）
     *         fileUrl   —— GET 可访问地址（7 天，前端存它用于图片展示）
     */
    @PostMapping("/uploadUrl")
    public R getUploadUrl(@RequestParam("fileName") String fileName) {
        try {
            Map<String, String> urls = fileUploadService.getUploadUrl(fileName);
            return R.ok()
                    .put("uploadUrl", urls.get("putUrl"))
                    .put("fileUrl", urls.get("getUrl"));
        } catch (Exception e) {
            return R.error("生成上传地址失败：" + e.getMessage());
        }
    }

}
