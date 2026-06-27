import { Card } from 'antd';
import type { PropsWithChildren, ReactNode } from 'react';

interface WorkbenchLayoutProps extends PropsWithChildren {
  sidebar: ReactNode;
  executionPanel: ReactNode;
}

export function WorkbenchLayout({ sidebar, children, executionPanel }: WorkbenchLayoutProps) {
  return (
    <div className="agent-workbench-grid">
      <Card className="agent-workbench-card" styles={{ body: { height: '100%', padding: 0 } }}>
        {sidebar}
      </Card>
      <Card className="agent-workbench-card" styles={{ body: { height: '100%', padding: 0 } }}>
        {children}
      </Card>
      <Card className="agent-workbench-card" styles={{ body: { height: '100%', padding: 0 } }}>
        {executionPanel}
      </Card>
    </div>
  );
}
