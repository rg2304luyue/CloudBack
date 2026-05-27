package org.cloudback.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(RedissonClient.class)
public class BloomFilterService {

    private final RedissonClient redissonClient;

    private static final String BLOOM_KEY = "bloom:product:ids";
    // 预计商品数量 10万，误判率 1%
    private static final long EXPECTED_INSERTIONS = 100_000;
    private static final double FALSE_PROBABILITY = 0.01;

    /**
     * 检查商品 ID 是否可能存在。
     * @return false = 绝对不存在，true = 可能存在（有 1% 误判）
     */
    public boolean mightContain(Long productId) {
        RBloomFilter<Long> filter = redissonClient.getBloomFilter(BLOOM_KEY);
        return filter.contains(productId);
    }

    /** 新增商品时注册 ID */
    public void addProductId(Long productId) {
        RBloomFilter<Long> filter = redissonClient.getBloomFilter(BLOOM_KEY);
        filter.add(productId);
    }

    /** 启动时初始化（仅首次创建时执行，后续重启复用已有数据） */
    @PostConstruct
    public void init() {
        RBloomFilter<Long> filter = redissonClient.getBloomFilter(BLOOM_KEY);
        if (!filter.isExists()) {
            filter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
            log.info("布隆过滤器初始化完成: key={}, expectedInsertions={}, fpp={}",
                    BLOOM_KEY, EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        }
    }
}
