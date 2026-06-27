package com.xiaoai.agent.collaboration.controller;

import com.xiaoai.agent.collaboration.entity.CollaborationTemplate;
import com.xiaoai.agent.collaboration.model.CollaborationTemplateResponse;
import com.xiaoai.agent.collaboration.service.CollaborationTemplateService;
import com.xiaoai.agent.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationTemplateControllerTest {

    private final CollaborationTemplateService service = mock(CollaborationTemplateService.class);
    private final CollaborationTemplateController controller = new CollaborationTemplateController(service);

    @Test
    void listTemplatesShouldDelegateWithDomainCode() {
        when(service.listActiveTemplates("software_development")).thenReturn(List.of(
                CollaborationTemplateResponse.builder()
                        .templateId(2L)
                        .templateCode("software_requirement_to_delivery")
                        .templateName("Software Requirement To Delivery")
                        .domainCode("software_development")
                        .strategyType("orchestrated_team")
                        .status("active")
                        .build()
        ));

        ApiResponse<List<CollaborationTemplateResponse>> result = controller.listTemplates("software_development");

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getTemplateCode()).isEqualTo("software_requirement_to_delivery");
        verify(service).listActiveTemplates("software_development");
    }

    @Test
    void getTemplateShouldReturnTemplateResponse() {
        CollaborationTemplate template = new CollaborationTemplate();
        template.setId(2L);
        template.setTemplateCode("software_requirement_to_delivery");
        template.setTemplateJson("{\"stages\":[]}");
        when(service.getTemplate(2L)).thenReturn(template);

        ApiResponse<CollaborationTemplateResponse> result = controller.getTemplate(2L);

        assertThat(result.getData().getTemplateId()).isEqualTo(2L);
        assertThat(result.getData().getTemplateJson()).contains("stages");
        verify(service).getTemplate(2L);
    }
}
