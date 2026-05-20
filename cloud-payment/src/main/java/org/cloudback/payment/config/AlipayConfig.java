package org.cloudback.payment.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlipayConfig {

    @Bean
    public AlipayClient alipayClient(AlipayProperties props) {
        return new DefaultAlipayClient(
                props.getGateway(),
                props.getAppId(),
                props.getPrivateKey(),
                "json", "UTF-8",
                props.getPublicKey(),
                "RSA2");
    }
}
