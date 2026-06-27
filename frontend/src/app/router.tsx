import { lazy, Suspense } from 'react';
import { Spin } from 'antd';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { App } from './App';

const AgentWorkbenchPage = lazy(() =>
  import('../pages/agent-workbench/AgentWorkbenchPage').then((module) => ({ default: module.AgentWorkbenchPage })),
);
const KnowledgePage = lazy(() =>
  import('../pages/knowledge/KnowledgePage').then((module) => ({ default: module.KnowledgePage })),
);
const ProjectAssistantConfigPage = lazy(() =>
  import('../pages/agent-config/ProjectAssistantConfigPage').then((module) => ({
    default: module.ProjectAssistantConfigPage,
  })),
);
const TaskDetailPage = lazy(() =>
  import('../pages/task-detail/TaskDetailPage').then((module) => ({ default: module.TaskDetailPage })),
);
const ToolAuditPage = lazy(() =>
  import('../pages/tool-audit/ToolAuditPage').then((module) => ({ default: module.ToolAuditPage })),
);
const CollaborationSessionPage = lazy(() =>
  import('../pages/collaboration/CollaborationSessionPage').then((module) => ({
    default: module.CollaborationSessionPage,
  })),
);
const CollaborationCreatePage = lazy(() =>
  import('../pages/collaboration/CollaborationCreatePage').then((module) => ({
    default: module.CollaborationCreatePage,
  })),
);

function withRouteLoading(element: React.ReactNode) {
  return <Suspense fallback={<Spin style={{ display: 'block', margin: '80px auto' }} />}>{element}</Suspense>;
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      { index: true, element: withRouteLoading(<AgentWorkbenchPage />) },
      { path: 'workbench', element: withRouteLoading(<AgentWorkbenchPage />) },
      { path: 'knowledge', element: withRouteLoading(<KnowledgePage />) },
      { path: 'agent-config', element: withRouteLoading(<ProjectAssistantConfigPage />) },
      { path: 'tool-audit', element: withRouteLoading(<ToolAuditPage />) },
      { path: 'tasks/:taskId', element: withRouteLoading(<TaskDetailPage />) },
      { path: 'collaboration', element: withRouteLoading(<CollaborationCreatePage />) },
      { path: 'collaboration/:sessionId', element: withRouteLoading(<CollaborationSessionPage />) },
      { path: '*', element: <Navigate to="/workbench" replace /> },
    ],
  },
]);
