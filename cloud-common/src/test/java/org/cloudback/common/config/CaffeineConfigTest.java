package org.cloudback.common.config;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CaffeineConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CaffeineConfig.class)
            .withBean("redisTemplate", RedisTemplate.class, () -> mock(RedisTemplate.class));

    @Test
    void startsWithoutRedissonOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader("org.redisson"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("productDetailTwoLevel");
                    assertThat(context).doesNotHaveBean("cartTwoLevel");
                });
    }

    @Test
    void createsCachesWhenRedissonIsAvailable() {
        runner.withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(TwoLevelCacheService.class)).hasSize(4);
                    assertThat(context).hasBean("productDetailCache");
                    assertThat(context).hasBean("hotProductsCache");
                    assertThat(context).hasBean("productIdListCache");
                    assertThat(context).hasBean("cartCache");
                });
    }

    @Test
    void allowsMissingRedissonBeanWhenClassesAreAvailable() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(TwoLevelCacheService.class)).hasSize(4);
        });
    }
}
