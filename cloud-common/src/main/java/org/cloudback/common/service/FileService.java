package org.cloudback.common.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(MinioClient.class)
public class FileService {

    /** 允许的图片扩展名（小写，不含点） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /** 扩展名 → 固定 Content-Type（不信任客户端上传的 contentType，防存储型 XSS） */
    private static final Map<String, String> CONTENT_TYPE_MAP = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif");

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.endpoint}")
    private String endpoint;

    public String upload(MultipartFile file, String prefix) {
        if (file.isEmpty()) {
            throw new BusinessException("请选择文件");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("仅支持 jpg/jpeg/png/webp/gif 图片格式");
        }
        byte[] header = readHeader(file);
        if (!matchesMagic(ext, header)) {
            log.warn("文件内容与扩展名不匹配，拒绝上传: ext={}, originalName={}", ext, file.getOriginalFilename());
            throw new BusinessException("文件内容与扩展名不匹配");
        }
        String objectName = prefix + "/" + UUID.randomUUID() + "." + ext;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(CONTENT_TYPE_MAP.get(ext))
                    .build());
            return "http://" + endpoint + "/" + bucket + "/" + objectName;
        } catch (Exception e) {
            log.error("上传文件到 MinIO 失败", e);
            throw new RuntimeException("上传失败，请重试", e);
        }
    }

    private String extractExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            throw new BusinessException("文件缺少扩展名");
        }
        return originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(header);
            if (read < 0) {
                return new byte[0];
            }
            byte[] result = new byte[read];
            System.arraycopy(header, 0, result, 0, read);
            return result;
        } catch (IOException e) {
            throw new BusinessException("文件读取失败，请重试");
        }
    }

    /** 魔数嗅探：校验文件头字节与声明的图片类型一致 */
    private boolean matchesMagic(String ext, byte[] header) {
        return switch (ext) {
            case "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47);
            case "gif" -> startsWith(header, 0x47, 0x49, 0x46, 0x38);
            case "webp" -> startsWith(header, 0x52, 0x49, 0x46, 0x46) // RIFF
                    && header.length >= 12
                    && header[8] == 0x57 && header[9] == 0x45  // WE
                    && header[10] == 0x42 && header[11] == 0x50; // BP
            default -> false;
        };
    }

    private boolean startsWith(byte[] data, int... magic) {
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((data[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
