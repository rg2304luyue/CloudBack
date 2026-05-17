package org.cloudback.payment.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.cloudback.common.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment")
public class Payment extends BaseEntity {
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    private String payMethod;
    private Integer status;     // 0-待支付 1-支付成功 2-支付失败
    private String tradeNo;     // 第三方交易号
    private LocalDateTime payTime;
}
