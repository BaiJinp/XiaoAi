package com.xiaoai.agent.test;

import org.springframework.test.util.ReflectionTestUtils;

public final class TestReflectionUtils {

    private TestReflectionUtils() {
    }

    public static void injectBaseMapper(Object target, Object mapper) {
        ReflectionTestUtils.setField(target, "baseMapper", mapper);
    }
}
