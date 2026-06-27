package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.collaboration.entity.CollaborationTemplate;
import com.xiaoai.agent.collaboration.mapper.CollaborationTemplateMapper;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollaborationTemplateServiceImplTest {

    private final CollaborationTemplateMapper mapper = mock(CollaborationTemplateMapper.class);
    private final CollaborationTemplateServiceImpl service = new CollaborationTemplateServiceImpl();

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
    void getTemplateShouldReturnTenantScopedTemplate() {
        CollaborationTemplate template = new CollaborationTemplate();
        template.setId(1L);
        template.setTenantId(100L);
        template.setTemplateCode("project_delivery");
        template.setStrategyType("orchestrated_team");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(template);

        CollaborationTemplate result = service.getTemplate(1L);

        assertThat(result.getTemplateCode()).isEqualTo("project_delivery");
        assertThat(result.getStrategyType()).isEqualTo("orchestrated_team");
    }

    @Test
    void getTemplateShouldRejectMissingOrCrossTenantTemplate() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getTemplate(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Collaboration template not found");
    }

    @Test
    void listActiveTemplatesShouldReturnTenantActiveTemplates() {
        CollaborationTemplate template = new CollaborationTemplate();
        template.setId(2L);
        template.setTenantId(100L);
        template.setTemplateCode("software_requirement_to_delivery");
        template.setTemplateName("Software Requirement To Delivery");
        template.setDomainCode("software_development");
        template.setStrategyType("orchestrated_team");
        template.setTemplateJson("{\"stages\":[]}");
        template.setStatus("active");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(template));

        var result = service.listActiveTemplates("software_development");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTemplateId()).isEqualTo(2L);
        assertThat(result.get(0).getTemplateCode()).isEqualTo("software_requirement_to_delivery");
        assertThat(result.get(0).getTemplateJson()).contains("stages");
    }
}
