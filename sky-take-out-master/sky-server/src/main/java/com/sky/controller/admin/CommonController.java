package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class CommonController {
    
    @Autowired
    private AliOssUtil aliOssUtil;
    /**
     * 文件上传
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file){
        log.info("文件上传：{}", file.getOriginalFilename());
        try{
            //获取原始文件名后缀
            String extension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
            //用uuid作为文件名，防止生成的临时文件重复
            String objectName = UUID.randomUUID().toString() + extension;
            //上传文件
            String filePath = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(filePath);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }

}
