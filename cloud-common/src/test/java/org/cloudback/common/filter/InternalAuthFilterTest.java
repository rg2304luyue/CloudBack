package org.cloudback.common.filter;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/** 内部令牌缺失时必须拒绝启动，避免下游鉴权静默 fail-open。 */
class InternalAuthFilterTest {

    @Test
    void missingTokenRefusesToStart() {
        InternalAuthFilter filter = new InternalAuthFilter();
        ReflectionTestUtils.setField(filter, "internalToken", "");
        IllegalStateException error = assertThrows(IllegalStateException.class, filter::requireConfiguredToken);
        assertTrue(error.getMessage().contains("cloud.internal.token"));
    }

    @Test
    void blankTokenRefusesToStart() {
        InternalAuthFilter filter = new InternalAuthFilter();
        ReflectionTestUtils.setField(filter, "internalToken", "   ");
        assertThrows(IllegalStateException.class, filter::requireConfiguredToken);
    }

    @Test
    void configuredTokenStarts() {
        InternalAuthFilter filter = new InternalAuthFilter();
        ReflectionTestUtils.setField(filter, "internalToken", "s3cret-token");
        assertDoesNotThrow(filter::requireConfiguredToken);
    }
}