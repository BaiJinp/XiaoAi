package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.collaboration.entity.ArtifactType;
import com.xiaoai.agent.collaboration.mapper.ArtifactTypeMapper;
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

class ArtifactTypeServiceImplTest {

    private final ArtifactTypeMapper mapper = mock(ArtifactTypeMapper.class);
    private final ArtifactTypeServiceImpl service = new ArtifactTypeServiceImpl();

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
    void getArtifactTypeShouldReturnTenantScopedType() {
        ArtifactType artifactType = new ArtifactType();
        artifactType.setId(1L);
        artifactType.setTenantId(100L);
        artifactType.setTypeCode("delivery_summary");
        artifactType.setTypeName("交付总结");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(artifactType);

        ArtifactType result = service.getArtifactType(1L);

        assertThat(result.getTypeCode()).isEqualTo("delivery_summary");
        assertThat(result.getTypeName()).isEqualTo("交付总结");
    }

    @Test
    void getArtifactTypeShouldRejectMissingOrCrossTenantType() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getArtifactType(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Artifact type not found");
    }
}
