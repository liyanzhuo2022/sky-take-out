package com.sky.controller.admin.Common;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class CommonController {

    /**
     * 文件上传：目前实现方式是上传到本地文件夹
     * @param file
     * */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file) {
        log.info("收到上传请求：{}", file);

        // 1. 空文件校验
        if (file == null || file.isEmpty()) {
            return Result.error("上传失败：文件为空");
        }

        try {
            // 2. 文件名与扩展名处理
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                return Result.error("上传失败：文件名无效");
            }

            String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            if (!extension.equals(".jpg") && !extension.equals(".jpeg") && !extension.equals(".png")) {
                return Result.error("上传失败：仅支持 .jpg/.jpeg/.png 文件");
            }

            String newFileName = UUID.randomUUID().toString() + extension;

            // 3. 构造上传目录路径（通用 + 可部署）
            String uploadBaseDir = System.getProperty("user.dir") + "/sky-server/src/main/resources/upload/";
            Path uploadPath = Paths.get(uploadBaseDir).normalize();

            File uploadDir = uploadPath.toFile();
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                if (created) {
                    log.info("上传目录不存在，已创建：{}", uploadPath);
                } else {
                    log.warn("上传目录不存在，创建失败：{}", uploadPath);
                    return Result.error("上传失败：服务器目录不可用");
                }
            }

            // 4. 目标文件路径（防路径遍历攻击）
            Path targetPath = uploadPath.resolve(newFileName).normalize();

            // 5. 保存文件
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件上传成功，文件名：{}", newFileName);

            // 6. 构造返回路径（相对路径，可由静态资源访问）
            String fileUrl = "/static/" + newFileName;
            return Result.success(fileUrl);

        } catch (IOException e) {
            log.error("文件上传失败：IO异常", e);
            return Result.error("上传失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("文件上传失败：未知异常", e);
            return Result.error("上传失败：服务器错误");
        }
    }


}
