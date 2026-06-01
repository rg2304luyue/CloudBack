package org.cloudback.payment.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {
    private String appId;

    /** RSA 私钥，禁止打印到日志 */
    @ToString.Exclude
    private String privateKey;

    private String publicKey;
    private String gateway;
    private String notifyUrl;
    private String returnUrl;
}
