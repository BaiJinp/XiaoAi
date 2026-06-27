export interface DemoScenario {
  title: string;
  description: string;
  prompt: string;
  templateCode?: string;
  strategyType?: string;
}

export const demoScenarios: DemoScenario[] = [
  {
    title: '会议纪要转任务',
    description: '提取行动项、责任人和截止时间，并在需要时发起审批。',
    prompt:
      '请根据以下会议纪要提取行动项，生成任务草稿，并在需要时发起审批。\n\n会议纪要：\n- 本周完成需求评审和技术方案初稿。\n- 张三负责在周五前补充接口清单。\n- 李四负责确认项目延期风险和资源缺口。\n- 如果需要创建项目管理系统任务，请先发起审批。',
  },
  {
    title: '项目周报生成',
    description: '根据项目资料生成可直接复用的周报草稿。',
    prompt:
      '请根据本周项目资料和任务状态生成项目周报，标注风险和不确定信息。\n\n本周资料：\n- 完成企业数字员工平台需求分析、技术方案和 MVP 任务拆分。\n- 后端已打通 Agent、任务账本、审批、工具、模型和知识库最小链路。\n- 前端正在建设 Agent 工作台、运行事件、审批卡片和交付物预览。\n- 风险：真实模型、文件上传、HTTP 工具接入后的边界仍需验证。',
  },
  {
    title: '项目风险分析',
    description: '识别延期、权限和集成风险，给出依据和建议动作。',
    prompt:
      '请分析当前项目延期风险，给出依据、影响和建议动作。\n\n当前情况：\n- MVP 目标是跑通项目助理 Agent 端到端闭环。\n- 后端最小链路已完成，但真实模型、知识文件上传和 HTTP 工具还需要联调。\n- 前端仍需补齐知识入口、配置页、任务详情页和完整构建验证。',
  },
  {
    title: '需求文档到协作交付',
    description: '基于需求文档启动一次通用多 Agent 协作交付，串联分析、设计、开发、测试和审查门禁。',
    prompt:
      '请基于以下需求文档启动一次多 Agent 协作交付，完成需求分析、技术方案、代码实现、测试验证和交付审查。\n\n需求文档：\n- 目标：把用户需求转化为可交付的软件功能。\n- 约束：保持通用 Agent 协作抽象，不将平台写死为软件研发专用系统。\n- 交付：需求分析、技术方案、实现说明、测试报告和交付总结。',
    templateCode: 'software_requirement_to_delivery',
    strategyType: 'orchestrated_team',
  },
];
