# Agent MVP Frontend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a user-facing Agent workbench MVP that proves the product is a runnable project assistant Agent, not a management platform.

**Current status:** This plan is the frontend execution baseline for the Agent-first pivot. The project assistant Workbench, task detail page, runtime timeline, approval card, artifact preview, minimal knowledge entry, minimal project assistant config page, App Shell navigation, and route lazy loading have been implemented and verified. Further frontend work should prioritize Agent usefulness and end-to-end smoke quality before adding platform/admin surfaces.

**Architecture:** Use a React single-page app focused on the Agent execution experience: chat input, runtime event timeline, approval cards, and artifact preview. Keep admin/config pages minimal and only support what is necessary for the project assistant MVP.

**Tech Stack:** React + TypeScript + Vite, Ant Design + Ant Design X, TanStack Query, Zustand, React Router, SSE/EventSource, react-markdown, ECharts optional.

---

## Product Direction

The frontend MVP must make users feel: **“This Agent can help me push project work forward.”**

Do not start with a traditional admin dashboard. Start with an Agent workbench.

Primary user flow:

```text
User provides meeting notes / project request / project material
        ↓
Agent creates a plan and starts a task run
        ↓
Frontend shows progress, steps, tool calls, knowledge sources, approvals
        ↓
User approves or rejects high-risk actions when needed
        ↓
Agent resumes and produces a reusable artifact
        ↓
User copies/downloads/uses the result
```

P0 scenarios:

1. Meeting notes to action items and task creation.
2. Project weekly report generation.
3. Project risk analysis.
4. High-risk action approval and resume.

---

## UX Priority

P0 pages:

1. Agent Workbench
2. Task Run Detail
3. Inline Approval Card
4. Artifact Preview
5. Minimal Knowledge Upload Entry
6. Minimal Project Assistant Config

P1/P2 pages such as model management, tool registry, policy management, audit dashboard, tenant management, and cost management must not block the MVP demo.

---

### Task 1: Create Frontend Project Skeleton

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/index.html`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/app/App.tsx`
- Create: `frontend/src/app/router.tsx`
- Create: `frontend/src/app/providers.tsx`
- Create: `frontend/src/styles/global.css`

**Step 1: Initialize Vite React TypeScript app files**

Use these dependencies:

```json
{
  "dependencies": {
    "@ant-design/icons": "latest",
    "@ant-design/x": "latest",
    "@tanstack/react-query": "latest",
    "antd": "latest",
    "axios": "latest",
    "dayjs": "latest",
    "echarts": "latest",
    "react": "latest",
    "react-dom": "latest",
    "react-markdown": "latest",
    "react-router-dom": "latest",
    "zustand": "latest"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "latest",
    "@testing-library/react": "latest",
    "@testing-library/user-event": "latest",
    "@types/react": "latest",
    "@types/react-dom": "latest",
    "@vitejs/plugin-react": "latest",
    "typescript": "latest",
    "vite": "latest",
    "vitest": "latest"
  }
}
```

**Step 2: Create application providers**

`frontend/src/app/providers.tsx` should wrap:

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App as AntdApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { PropsWithChildren } from 'react';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <ConfigProvider locale={zhCN} theme={{ token: { colorPrimary: '#1677ff' } }}>
      <AntdApp>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}
```

**Step 3: Run install and type check**

Run:

```bash
cd frontend && npm install
cd frontend && npm run typecheck
```

Expected: dependencies install and typecheck passes.

---

### Task 2: Define Frontend API and Domain Types

**Files:**
- Create: `frontend/src/types/api.ts`
- Create: `frontend/src/types/agent.ts`
- Create: `frontend/src/types/task.ts`
- Create: `frontend/src/types/runtime-event.ts`
- Create: `frontend/src/types/approval.ts`
- Create: `frontend/src/types/artifact.ts`
- Create: `frontend/src/services/http.ts`
- Create: `frontend/src/services/task-api.ts`
- Create: `frontend/src/services/approval-api.ts`
- Create: `frontend/src/services/agent-api.ts`
- Create: `frontend/src/services/knowledge-api.ts`

**Step 1: Define shared API response shape**

`frontend/src/types/api.ts`:

```ts
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}
```

**Step 2: Define runtime event model**

`frontend/src/types/runtime-event.ts`:

```ts
export type RuntimeEventType =
  | 'RUN_STARTED'
  | 'PLAN_CREATED'
  | 'STEP_STARTED'
  | 'STEP_COMPLETED'
  | 'KNOWLEDGE_RETRIEVED'
  | 'MODEL_CALLED'
  | 'TOOL_CALL_REQUESTED'
  | 'TOOL_CALL_BLOCKED'
  | 'TOOL_CALL_COMPLETED'
  | 'APPROVAL_REQUIRED'
  | 'RUN_SUSPENDED'
  | 'RUN_RESUMED'
  | 'RUN_COMPLETED'
  | 'RUN_FAILED'
  | 'RUN_CANCELLED';

export interface RuntimeEvent {
  id: string;
  tenantId: number;
  taskId: number;
  runId: number;
  eventType: RuntimeEventType;
  title?: string;
  message?: string;
  payload?: Record<string, unknown>;
  createTime?: string;
}
```

**Step 3: Implement HTTP client**

`frontend/src/services/http.ts`:

```ts
import axios from 'axios';
import type { ApiResponse } from '../types/api';

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'X-Tenant-Id': import.meta.env.VITE_DEV_TENANT_ID || '100',
    'X-User-Id': import.meta.env.VITE_DEV_USER_ID || '1000',
  },
});

export async function unwrap<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await request;
  if (response.data.code !== 0) {
    throw new Error(response.data.message || '请求失败');
  }
  return response.data.data;
}
```

**Step 4: Run type check**

Run:

```bash
cd frontend && npm run typecheck
```

Expected: PASS.

---

### Task 3: Build Agent Workbench Shell

**Files:**
- Create: `frontend/src/pages/agent-workbench/AgentWorkbenchPage.tsx`
- Create: `frontend/src/pages/agent-workbench/components/WorkbenchLayout.tsx`
- Create: `frontend/src/pages/agent-workbench/components/ConversationSidebar.tsx`
- Create: `frontend/src/pages/agent-workbench/components/ExecutionPanel.tsx`
- Modify: `frontend/src/app/router.tsx`

**Step 1: Add route**

Route `/` and `/workbench` to `AgentWorkbenchPage`.

**Step 2: Implement three-column layout**

Workbench layout:

```text
Left: Task/conversation list, 260px
Center: Chat and input, flexible
Right: Execution panel and artifact preview, 360px
```

Use Ant Design `Layout`, `Card`, `Splitter` if available, otherwise CSS grid.

**Step 3: Add empty states**

The empty state must guide users toward project assistant scenarios:

```text
试试：
- 根据这段会议纪要生成行动项并创建任务
- 根据本周资料生成项目周报
- 分析当前项目延期风险
```

**Step 4: Run UI smoke test**

Run:

```bash
cd frontend && npm run dev
```

Expected: browser shows the Agent Workbench, not an admin dashboard.

---

### Task 4: Implement Chat Input and Message Timeline

**Files:**
- Create: `frontend/src/features/chat/components/ChatTimeline.tsx`
- Create: `frontend/src/features/chat/components/ChatComposer.tsx`
- Create: `frontend/src/features/chat/chat-store.ts`
- Modify: `frontend/src/pages/agent-workbench/AgentWorkbenchPage.tsx`
- Test: `frontend/src/features/chat/ChatComposer.test.tsx`

**Step 1: Write failing test**

Test that submitting text calls `onSubmit` and clears the input.

```tsx
it('submits user input and clears composer', async () => {
  const onSubmit = vi.fn();
  render(<ChatComposer onSubmit={onSubmit} disabled={false} />);
  await userEvent.type(screen.getByPlaceholderText(/输入项目助理任务/), '生成项目周报');
  await userEvent.click(screen.getByRole('button', { name: /发送/ }));
  expect(onSubmit).toHaveBeenCalledWith('生成项目周报');
  expect(screen.getByPlaceholderText(/输入项目助理任务/)).toHaveValue('');
});
```

**Step 2: Run test to verify it fails**

Run:

```bash
cd frontend && npm test -- ChatComposer.test.tsx
```

Expected: FAIL because component does not exist.

**Step 3: Implement composer**

Use Ant Design X Sender if compatible; otherwise use Ant Design `Input.TextArea` + `Button`.

**Step 4: Implement message timeline**

Support message types:

```ts
type ChatMessageRole = 'user' | 'assistant' | 'system';
```

Render assistant messages with `react-markdown`.

**Step 5: Run tests**

Run:

```bash
cd frontend && npm test -- ChatComposer.test.tsx
```

Expected: PASS.

---

### Task 5: Connect Task Creation API

**Files:**
- Modify: `frontend/src/services/task-api.ts`
- Create: `frontend/src/features/task-ledger/hooks/useCreateTask.ts`
- Modify: `frontend/src/pages/agent-workbench/AgentWorkbenchPage.tsx`
- Test: `frontend/src/features/task-ledger/useCreateTask.test.tsx`

**Step 1: Define create task request**

```ts
export interface CreateTaskRequest {
  agentId: number;
  input: string;
  channel: 'web';
}
```

**Step 2: Implement createTask**

Use the actual backend endpoint name from `TaskController`. If the backend endpoint differs, adapt this service only; do not leak backend path details into components.

**Step 3: Implement mutation hook**

Use TanStack Query `useMutation`.

**Step 4: Wire composer submit**

When user sends text:

1. Add user message locally.
2. Call create task API.
3. Store returned taskId/runId.
4. Start runtime SSE subscription.

**Step 5: Test**

Mock `task-api.ts`, submit message, assert mutation is called and pending state disables composer.

---

### Task 6: Implement Runtime SSE Client and Store

**Files:**
- Create: `frontend/src/services/runtime-sse.ts`
- Create: `frontend/src/features/runtime-events/runtime-event-store.ts`
- Create: `frontend/src/features/runtime-events/runtime-event-presenter.ts`
- Test: `frontend/src/features/runtime-events/runtime-event-presenter.test.ts`

**Step 1: Write event presenter tests**

Convert technical events into user-facing text:

```ts
expect(toUserEvent({ eventType: 'APPROVAL_REQUIRED', message: 'create task' })).toEqual({
  status: 'waiting',
  title: '需要审批',
});
```

**Step 2: Implement SSE client**

Use `EventSource`:

```ts
export function subscribeRuntimeEvents(taskId: number, handlers: RuntimeEventHandlers) {
  const source = new EventSource(`/api/v1/tasks/${taskId}/events/stream`);
  source.onmessage = (event) => handlers.onEvent(JSON.parse(event.data));
  source.onerror = () => handlers.onError?.();
  return () => source.close();
}
```

If backend path differs, change only this file.

**Step 3: Implement Zustand event store**

Store by `taskId`, append events idempotently by event id.

**Step 4: Run tests**

Run:

```bash
cd frontend && npm test -- runtime-event-presenter.test.ts
```

Expected: PASS.

---

### Task 7: Build Execution Panel and Step Timeline

**Files:**
- Create: `frontend/src/features/runtime-events/components/ExecutionTimeline.tsx`
- Create: `frontend/src/features/runtime-events/components/RuntimeStatusBadge.tsx`
- Modify: `frontend/src/pages/agent-workbench/components/ExecutionPanel.tsx`
- Test: `frontend/src/features/runtime-events/ExecutionTimeline.test.tsx`

**Step 1: Write failing test**

Given events `RUN_STARTED`, `KNOWLEDGE_RETRIEVED`, `APPROVAL_REQUIRED`, render readable step titles.

**Step 2: Implement status mapping**

Mapping:

```text
RUN_STARTED -> 运行开始
KNOWLEDGE_RETRIEVED -> 已检索项目资料
MODEL_CALLED -> 已生成分析内容
TOOL_CALL_BLOCKED -> 工具调用被策略拦截
APPROVAL_REQUIRED -> 等待审批
RUN_COMPLETED -> 已完成
RUN_FAILED -> 失败
```

**Step 3: Render timeline**

Use Ant Design `Timeline` and `Tag`.

**Step 4: Run tests**

Expected: PASS.

---

### Task 8: Build Inline Approval Card

**Files:**
- Create: `frontend/src/features/approval-card/components/ApprovalCard.tsx`
- Create: `frontend/src/features/approval-card/hooks/useHandleApproval.ts`
- Modify: `frontend/src/services/approval-api.ts`
- Modify: `frontend/src/features/chat/components/ChatTimeline.tsx`
- Test: `frontend/src/features/approval-card/ApprovalCard.test.tsx`

**Step 1: Write failing test**

Render approval reason, risk level, approver, approve/reject buttons.

**Step 2: Implement approval API service**

Functions:

```ts
handleApproval(requestId: number, action: 'approve' | 'reject', comment?: string)
```

**Step 3: Implement ApprovalCard**

Card fields:

```text
Agent 想执行什么
风险等级
为什么需要审批
影响范围
审批人
同意执行 / 拒绝
```

**Step 4: Wire approval event**

When runtime event is `APPROVAL_REQUIRED`, show `ApprovalCard` in chat timeline and execution panel.

**Step 5: Run tests**

Expected: PASS.

---

### Task 9: Build Artifact Preview

**Files:**
- Create: `frontend/src/features/artifact-preview/components/ArtifactPreview.tsx`
- Create: `frontend/src/features/artifact-preview/components/MarkdownArtifact.tsx`
- Create: `frontend/src/features/artifact-preview/components/ActionItemsArtifact.tsx`
- Modify: `frontend/src/services/task-api.ts`
- Test: `frontend/src/features/artifact-preview/ArtifactPreview.test.tsx`

**Step 1: Write failing test**

Given a markdown artifact, render heading and copy button.

**Step 2: Implement artifact types**

```ts
export interface TaskArtifact {
  id: number;
  taskId: number;
  artifactType: 'markdown' | 'action_items' | 'risk_list' | 'task_creation_result';
  title: string;
  content: string;
  createTime?: string;
}
```

**Step 3: Implement preview**

Support:

- Markdown weekly report.
- Action items table.
- Risk list table.
- Plain JSON fallback.

**Step 4: Add copy action**

Use browser clipboard API with error fallback.

**Step 5: Run tests**

Expected: PASS.

---

### Task 10: Build Minimal Knowledge Upload Entry

**Files:**
- Create: `frontend/src/pages/knowledge/KnowledgePage.tsx`
- Create: `frontend/src/features/knowledge-source/components/KnowledgeUploadCard.tsx`
- Modify: `frontend/src/services/knowledge-api.ts`
- Modify: `frontend/src/app/router.tsx`

**Step 1: Add upload UI**

MVP can start with text input upload if backend file upload is not ready:

```text
知识库名称
文档标题
文档文本内容
提交入库
```

**Step 2: Implement API call**

Use existing backend knowledge document text ingest endpoint. If backend only supports service-level code and no controller endpoint, create a TODO in the plan execution result; do not fake success in UI.

**Step 3: Add simple retrieval debug**

Allow user to enter keyword and preview matching chunks with source title.

**Step 4: Run UI smoke test**

Expected: user can add project text material and later use it in Agent task.

---

### Task 11: Build Minimal Project Assistant Config Page

**Files:**
- Create: `frontend/src/pages/agent-config/ProjectAssistantConfigPage.tsx`
- Create: `frontend/src/features/agent-config/components/ProjectAssistantForm.tsx`
- Modify: `frontend/src/services/agent-api.ts`
- Modify: `frontend/src/app/router.tsx`

**Step 1: Keep form minimal**

Only include fields needed for MVP:

```text
Agent 名称
角色描述
主要职责
不处理事项
默认知识库
默认工具
发布用户范围
```

Do not build full template market, policy designer, model console, or tenant admin.

**Step 2: Implement create draft action**

Use backend Agent draft create endpoint.

**Step 3: Implement version/publish actions only if backend endpoints are ready**

If endpoints are not ready, show disabled button with clear text:

```text
生成版本和发布将在下一步接入
```

**Step 4: Run UI smoke test**

Expected: project assistant draft can be created or UI clearly says which backend capability is missing.

---

### Task 12: Add Task Detail Page

**Files:**
- Create: `frontend/src/pages/task-detail/TaskDetailPage.tsx`
- Modify: `frontend/src/services/task-api.ts`
- Modify: `frontend/src/app/router.tsx`

**Step 1: Add route**

Route `/tasks/:taskId` to `TaskDetailPage`.

**Step 2: Display task facts**

Show:

```text
任务标题 / 输入
状态
创建时间
当前 runId
执行事件
审批状态
交付物
```

**Step 3: Reuse components**

Reuse:

- `ExecutionTimeline`
- `ApprovalCard`
- `ArtifactPreview`

Do not duplicate timeline rendering.

**Step 4: Run UI smoke test**

Expected: opening a task detail page shows same event and artifact state as workbench.

---

### Task 13: Add MVP Demo Fixtures

**Files:**
- Create: `frontend/src/mocks/demo-scenarios.ts`
- Create: `frontend/src/features/demo/DemoScenarioPanel.tsx`
- Modify: `frontend/src/pages/agent-workbench/AgentWorkbenchPage.tsx`

**Step 1: Define three demo prompts**

```ts
export const demoScenarios = [
  {
    title: '会议纪要转任务',
    prompt: '请根据以下会议纪要提取行动项，生成任务草稿，并在需要时发起审批。\n\n...',
  },
  {
    title: '项目周报生成',
    prompt: '请根据本周项目资料和任务状态生成项目周报，标注风险和不确定信息。',
  },
  {
    title: '项目风险分析',
    prompt: '请分析当前项目延期风险，给出依据、影响和建议动作。',
  },
];
```

**Step 2: Add scenario buttons**

Clicking a scenario fills the composer, not auto-submit.

**Step 3: Run UI smoke test**

Expected: a first-time user can understand what the product does within 30 seconds.

---

### Task 14: Add Build, Test, and Verification Scripts

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/vitest.config.ts`

**Step 1: Add scripts**

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "typecheck": "tsc -b --pretty false",
    "test": "vitest run",
    "test:watch": "vitest"
  }
}
```

**Step 2: Add Vitest setup**

Import `@testing-library/jest-dom`.

**Step 3: Run full frontend checks**

Run:

```bash
cd frontend && npm run typecheck
cd frontend && npm test
cd frontend && npm run build
```

Expected: all pass.

---

### Task 15: Update Project Documentation

**Files:**
- Modify: `docs/task/20260604_企业数字员工平台_任务.md`
- Modify: `docs/design/20260604_企业数字员工平台_技术.md` only after user confirms design sync

**Step 1: Update task document**

Record frontend direction:

```text
MVP 前端重点调整为可运行 Agent 工作台，不优先建设完整管理后台。
技术选型：React + TypeScript + Vite + Ant Design + Ant Design X + TanStack Query + Zustand + SSE。
```

**Step 2: Add task checklist**

Add P0 frontend tasks:

- Create frontend skeleton.
- Build Agent Workbench.
- Build chat composer and timeline.
- Connect task creation API.
- Connect Runtime SSE.
- Render execution timeline.
- Render approval card.
- Render artifact preview.
- Add knowledge upload entry.
- Add minimal project assistant config.
- Add task detail page.
- Add demo scenarios.
- Run frontend tests and build.

**Step 3: Do not update technical design silently if code/document conflict exists**

If current backend API paths differ from the frontend assumptions, list the mismatch and ask user before syncing design.

---

## Verification Gate

Before claiming frontend MVP is complete, run:

```bash
cd frontend && npm run typecheck
cd frontend && npm test
cd frontend && npm run build
```

If backend integration is available, also run backend tests:

```bash
cd backend && mvn -s ../maven-settings.xml test
```

Manual smoke test:

1. Open Agent Workbench.
2. Submit weekly report demo prompt.
3. Confirm task appears in workbench.
4. Confirm runtime events stream into execution panel.
5. Confirm approval card appears for high-risk tool action.
6. Approve action.
7. Confirm task resumes.
8. Confirm artifact preview renders final output.

---

## Non-Goals for MVP

Do not implement these before the Agent workbench runs end-to-end:

- Full tenant admin.
- Full user/role management.
- Full model provider console.
- Full policy designer.
- Full tool registry UI.
- Full audit dashboard.
- Full cost/FinOps dashboard.
- Full workflow canvas.
- Template marketplace.
- Multi-Agent management console.

These are platform features. MVP success is measured by whether the project assistant Agent can execute useful work and show its process clearly.
