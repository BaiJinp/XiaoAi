-- ============================================================
-- 通用多 Agent 协作场景种子：软件研发需求到交付
-- 说明：本文件只写入通用协作表，软件研发只是 domain/template 数据配置，底层不新增研发专用表。
-- ============================================================

-- 角色实例：software_development domain 下的数据配置，不是 Java 枚举。
INSERT INTO agent_role (
    tenant_id, role_code, role_name, domain_code, description, responsibility_text,
    input_artifact_types, output_artifact_types, status, created_by, updated_by
) VALUES
(100, 'software_product_manager', '软件产品经理', 'software_development', '负责需求澄清和需求分析', '识别目标、边界、用户价值和验收标准', '["requirement_document"]'::jsonb, '["requirement_analysis"]'::jsonb, 'active', 1, 1),
(100, 'software_architect', '软件架构师', 'software_development', '负责技术方案和接口契约', '基于需求分析设计技术方案、接口和关键业务流程', '["requirement_analysis"]'::jsonb, '["technical_design", "api_contract"]'::jsonb, 'active', 1, 1),
(100, 'software_backend_developer', '后端开发', 'software_development', '负责后端实现总结', '基于技术方案完成后端实现并输出实现摘要', '["technical_design", "api_contract"]'::jsonb, '["implementation_summary"]'::jsonb, 'active', 1, 1),
(100, 'software_frontend_developer', '前端开发', 'software_development', '负责前端实现总结', '基于技术方案完成前端实现并输出实现摘要', '["technical_design", "api_contract"]'::jsonb, '["implementation_summary"]'::jsonb, 'active', 1, 1),
(100, 'software_tester', '测试工程师', 'software_development', '负责测试设计与测试报告', '基于需求和实现摘要补充测试并输出测试报告', '["requirement_analysis", "implementation_summary"]'::jsonb, '["test_report"]'::jsonb, 'active', 1, 1),
(100, 'software_reviewer', '代码审查员', 'software_development', '负责质量审查报告', '基于技术方案、实现摘要和测试报告输出审查结论', '["technical_design", "implementation_summary", "test_report"]'::jsonb, '["review_report", "delivery_summary"]'::jsonb, 'active', 1, 1)
ON CONFLICT (tenant_id, role_code) DO NOTHING;

-- 交付物类型实例：使用通用 artifact_type 注册表。
INSERT INTO artifact_type (
    tenant_id, type_code, type_name, domain_code, schema_json, renderer_type, validator_json,
    status, created_by, updated_by
) VALUES
(100, 'requirement_document', '需求原文', 'software_development', '{"type":"markdown"}'::jsonb, 'markdown', '{}'::jsonb, 'active', 1, 1),
(100, 'requirement_analysis', '需求分析', 'software_development', '{"type":"markdown","sections":["goal","scope","acceptance"]}'::jsonb, 'markdown', '{}'::jsonb, 'active', 1, 1),
(100, 'technical_design', '技术方案', 'software_development', '{"type":"markdown","sections":["architecture","data","flow"]}'::jsonb, 'markdown', '{}'::jsonb, 'active', 1, 1),
(100, 'api_contract', '接口契约', 'software_development', '{"type":"json","required":["endpoints","errors"]}'::jsonb, 'json', '{}'::jsonb, 'active', 1, 1),
(100, 'implementation_summary', '实现摘要', 'software_development', '{"type":"markdown","sections":["changes","files","risks"]}'::jsonb, 'markdown', '{}'::jsonb, 'active', 1, 1),
(100, 'test_report', '测试报告', 'software_development', '{"type":"markdown","sections":["scope","result","evidence"]}'::jsonb, 'markdown', '{}'::jsonb, 'active', 1, 1),
(100, 'review_report', '审查报告', 'software_development', '{"type":"markdown","sections":["findings","verdict"]}'::jsonb, 'markdown', '{}'::jsonb, 'active', 1, 1),
(100, 'delivery_summary', '交付总结', 'software_development', '{"type":"markdown","sections":["summary","verification","nextSteps"]}'::jsonb, 'markdown', '{}'::jsonb, 'active', 1, 1)
ON CONFLICT (tenant_id, type_code) DO NOTHING;

-- 场景模板：研发全链路只作为 collaboration_template.template_json 数据存在。
INSERT INTO collaboration_template (
    tenant_id, template_code, template_name, domain_code, strategy_type, template_json,
    status, created_by, updated_by
) VALUES (
    100,
    'software_requirement_to_delivery',
    '需求文档到协作交付',
    'software_development',
    'orchestrated_team',
    '{
      "goal": "根据需求文档完成一次多角色协作交付",
      "maxDepth": 1,
      "maxThreads": 6,
      "gates": [
        {"gateCode": "requirement_confirmed", "gateName": "需求确认", "gateType": "manual_confirmation", "required": true},
        {"gateCode": "design_confirmed", "gateName": "方案确认", "gateType": "manual_confirmation", "required": true},
        {"gateCode": "implementation_done", "gateName": "实现完成", "gateType": "artifact_check", "required": true},
        {"gateCode": "tests_passed", "gateName": "测试通过", "gateType": "test_result", "required": true},
        {"gateCode": "delivery_confirmed", "gateName": "交付确认", "gateType": "manual_confirmation", "required": true}
      ],
      "stages": [
        {"stageCode": "requirement_analysis", "stageName": "需求分析", "roleCode": "software_product_manager", "createTask": true, "autoStartTask": true, "inputText": "基于用户需求输出 PRD、用户故事和验收标准。", "inputArtifactTypes": ["requirement_document"], "outputArtifactTypes": ["requirement_analysis"], "requiresGate": "requirement_confirmed", "gateName": "需求确认", "gateType": "manual_confirmation"},
        {"stageCode": "technical_design", "stageName": "技术方案", "roleCode": "software_architect", "createTask": true, "autoStartTask": true, "inputText": "基于已确认需求输出技术方案、接口契约和关键风险。", "inputArtifactTypes": ["requirement_analysis"], "outputArtifactTypes": ["technical_design", "api_contract"], "requiresGate": "design_confirmed", "gateName": "方案确认", "gateType": "manual_confirmation"},
        {"stageCode": "backend_implementation", "stageName": "后端实现", "roleCode": "software_backend_developer", "createTask": true, "autoStartTask": true, "inputText": "基于技术方案完成后端实现并输出实现摘要。", "inputArtifactTypes": ["technical_design", "api_contract"], "outputArtifactTypes": ["implementation_summary"], "requiresGate": "implementation_done", "gateName": "实现完成", "gateType": "artifact_check"},
        {"stageCode": "frontend_implementation", "stageName": "前端实现", "roleCode": "software_frontend_developer", "createTask": true, "autoStartTask": true, "inputText": "基于技术方案完成前端实现并输出实现摘要。", "inputArtifactTypes": ["technical_design", "api_contract"], "outputArtifactTypes": ["implementation_summary"], "requiresGate": "implementation_done", "gateName": "实现完成", "gateType": "artifact_check"},
        {"stageCode": "testing", "stageName": "测试验证", "roleCode": "software_tester", "createTask": true, "autoStartTask": true, "inputText": "基于验收标准和实现摘要输出测试计划、执行结果和风险。", "inputArtifactTypes": ["requirement_analysis", "implementation_summary"], "outputArtifactTypes": ["test_report"], "requiresGate": "tests_passed", "gateName": "测试通过", "gateType": "test_result"},
        {"stageCode": "review_and_delivery", "stageName": "审查交付", "roleCode": "software_reviewer", "createTask": true, "autoStartTask": true, "inputText": "基于技术方案、实现摘要和测试报告输出审查结论与交付总结。", "inputArtifactTypes": ["technical_design", "implementation_summary", "test_report"], "outputArtifactTypes": ["review_report", "delivery_summary"], "requiresGate": "delivery_confirmed", "gateName": "交付确认", "gateType": "manual_confirmation"}
      ]
    }'::jsonb,
    'active',
    1,
    1
)
ON CONFLICT (tenant_id, template_code) DO NOTHING;
