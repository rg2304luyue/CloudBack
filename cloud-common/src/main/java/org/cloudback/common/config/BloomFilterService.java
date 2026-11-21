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
     *
     * <p>语义是「false 才代表绝对不存在」，因此这里必须 fail-open：Redis 不可用或过滤器
     * 尚未建立时一律返回 true 让调用方回源 DB。否则一次 Redis 抖动就会让商品详情/列表/搜索
     * 全部 500，Redis 数据被清空后更会让整个商品目录变成 404，且没有自愈路径
     * （过滤器只在启动时灌数据）。</p>
     *
     * @return false = 确定不存在，true = 可能存在（约 1% 误判）或无法判定
     */
    public boolean mightContain(Long productId) {
        if (productId == null) {
            return false;
        }
        try {
            RBloomFilter<Long> filter = redissonClient.getBloomFilter(BLOOM_KEY);
            if (!filter.isExists()) {
                // Redis 数据丢失后过滤器为空：此时任何 contains 都会返回 false，
                // 不能据此判定商品不存在。
                return true;
            }
            return filter.contains(productId);
        } catch (Exception e) {
            log.warn("布隆过滤器不可用，按可能存在处理: productId={}, error={}", productId, e.getMessage());
            return true;
        }
    }

    /** 新增/回源命中商品时注册 ID（Redis 故障只记日志，不影响主流程） */
    public void addProductId(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            RBloomFilter<Long> filter = redissonClient.getBloomFilter(BLOOM_KEY);
            filter.add(productId);
        } catch (Exception e) {
            log.warn("写入布隆过滤器失败，忽略: productId={}, error={}", productId, e.getMessage());
        }
    }

    /** 启动时初始化（仅首次创建时执行，后续重启复用已有数据） */
    @PostConstruct
    public void init() {
        try {
            RBloomFilter<Long> filter = redissonClient.getBloomFilter(BLOOM_KEY);
            if (!filter.isExists()) {
                filter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
                log.info("布隆过滤器初始化完成: key={}, expectedInsertions={}, fpp={}",
                        BLOOM_KEY, EXPECTED_INSERTIONS, FALSE_PROBABILITY);
            }
        } catch (Exception e) {
            // 初始化失败不能让商品服务起不来：后续 mightContain 会 fail-open
            log.warn("布隆过滤器初始化失败，将按可能存在处理: error={}", e.getMessage());
        }
    }
}
