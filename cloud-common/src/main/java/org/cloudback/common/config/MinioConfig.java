package org.cloudback.common.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "minio", name = "endpoint")
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        String host = endpoint;
        int port = 9000;
        int colonIdx = endpoint.lastIndexOf(':');
        if (colonIdx > 0) {
            host = endpoint.substring(0, colonIdx);
            try {
                port = Integer.parseInt(endpoint.substring(colonIdx + 1));
            } catch (NumberFormatException ignored) {}
        }
        MinioClient client = MinioClient.builder()
                .endpoint(host, port, false)
                .credentials(accessKey, secretKey)
                .build();
        initBucket(client);
        return client;
    }

    private void initBucket(MinioClient client) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                String policy = """
                        {
                          "Version": "2012-10-17",
                          "Statement": [
                            {
                              "Effect": "Allow",
                              "Principal": {"AWS": ["*"]},
                              "Action": ["s3:GetObject"],
                              "Resource": ["arn:aws:s3:::%s/*"]
                            }
                          ]
                        }
                        """.formatted(bucket);
                client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
                log.info("MinIO bucket created with public-read policy: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket init failed (may already exist): {}", e.getMessage());
        }
    }

}
