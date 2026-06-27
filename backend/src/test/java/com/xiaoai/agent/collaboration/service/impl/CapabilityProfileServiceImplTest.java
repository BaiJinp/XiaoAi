package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.collaboration.entity.CapabilityProfile;
import com.xiaoai.agent.collaboration.mapper.CapabilityProfileMapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityProfileServiceImplTest {

    private final CapabilityProfileMapper mapper = mock(CapabilityProfileMapper.class);
    private final CapabilityProfileServiceImpl service = new CapabilityProfileServiceImpl();

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(service, mapper);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getProfileShouldReturnTenantScopedProfile() {
        CapabilityProfile profile = new CapabilityProfile();
        profile.setId(1L);
        profile.setTenantId(100L);
        profile.setProfileCode("generic_profile");
        profile.setProfileName("通用能力画像");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(profile);

        CapabilityProfile result = service.getProfile(1L);

        assertThat(result.getProfileCode()).isEqualTo("generic_profile");
        assertThat(result.getProfileName()).isEqualTo("通用能力画像");
    }

    @Test
    void getProfileShouldRejectMissingOrCrossTenantProfile() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getProfile(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Capability profile not found");
    }
}
