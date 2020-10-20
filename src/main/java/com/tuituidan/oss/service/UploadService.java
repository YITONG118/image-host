package com.tuituidan.oss.service;

import com.tuituidan.oss.bean.FileInfo;
import com.tuituidan.oss.exception.ImageHostException;
import com.tuituidan.oss.kit.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

/**
 * UploadService.
 *
 * @author zhujunhan
 * @version 1.0
 * @date 2020/10/14
 */
@Slf4j
@Service
public class UploadService {

    @Resource
    private MinioService minioService;

    @Resource
    private ElasticsearchService elasticsearchService;

    @Resource
    private FileCacheService fileCacheService;

    /**
     * base64数据上传.
     *
     * @param inputStream inputStream
     * @return String
     */
    public String uploadBase64(InputStream inputStream) {
        String base64Str;
        try (InputStream sourceIn = inputStream) {
            base64Str = IOUtils.toString(sourceIn, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw ImageHostException.builder().error("获取base64数据失败", ex).build();
        }
        Pair<String, String> base64 = StringKit.getBase64Info(base64Str);
        if (null == base64) {
            throw ImageHostException.builder().error("base64数据格式错误-{}", base64Str).build();
        }
        return upload(new FileInfo().setName("image." + base64.getLeft()).setBase64(base64.getRight()));
    }

    /**
     * 文件上传.
     *
     * @param fileInfo fileInfo
     * @return String
     */
    public String upload(FileInfo fileInfo) {
        String ext = FilenameUtils.getExtension(fileInfo.getName()).toLowerCase();
        if (FileTypeKit.isNotSupport(ext)) {
            throw ImageHostException.builder().tip("很抱歉，暂不支持上传该种类型的文件！").build();
        }
        fileInfo.setExt(ext);
        fileInfo.setId(IdKit.getId());
        byte[] sourceData = getDataFromFileInfo(fileInfo);
        // 是否压缩+文件md5来标识是否是同一个文件
        String md5 = StringKit.format("{}-{}", DigestUtils.md5Hex(sourceData), fileInfo.isCompress());
        String objName = fileCacheService.get(md5);
        if (StringUtils.isNotBlank(objName)) {
            // 已存在直接返回，不重复上传
            return minioService.getObjectUrl(objName);
        }
        if (fileInfo.isCompress()) {
            sourceData = CompressKit.compress(fileInfo.getExt(), sourceData);
        }
        objName = StringKit.getObjectName(fileInfo.getId(), fileInfo.getExt());
        try (InputStream inputStream = new ByteArrayInputStream(sourceData)) {
            // minio支持给文件打标签（老一些版本的minio不支持，但也不会报错），将这些信息也一并写入
            Map<String, String> tags = HashMapKit.newFixQuarterSize();
            tags.put("info", fileInfo.getTags());
            tags.put("compress", String.valueOf(fileInfo.isCompress()));
            tags.put("md5", md5);
            minioService.putObject(objName, tags, inputStream);
            elasticsearchService.asyncSaveFileDoc(objName, md5, fileInfo);
            fileCacheService.put(md5, objName);
            return minioService.getObjectUrl(objName);
        } catch (Exception ex) {
            throw ImageHostException.builder().error("上传文件到 MinIO 失败！", ex).build();
        }
    }

    private byte[] getDataFromFileInfo(FileInfo fileInfo) {
        try {
            // 如果是base64的，转换base64
            if (StringUtils.isNotBlank(fileInfo.getBase64())) {
                return Base64.getDecoder().decode(fileInfo.getBase64());
            }
            return fileInfo.getFile().getBytes();
        } catch (Exception ex) {
            throw ImageHostException.builder().error("获取文件数据失败！", ex).build();
        }
    }
}
