import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';

describe('App', () => {
  it('renders navigation links for core MVP pages', () => {
    render(
      <MemoryRouter initialEntries={['/workbench']}>
        <Routes>
          <Route path="/" element={<App />}>
            <Route path="workbench" element={<div>工作台内容</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', { name: 'Agent 工作台' })).toHaveAttribute('href', '/workbench');
    expect(screen.getByRole('link', { name: '知识入口' })).toHaveAttribute('href', '/knowledge');
    expect(screen.getByRole('link', { name: '项目助理配置' })).toHaveAttribute('href', '/agent-config');
    expect(screen.getByText('工作台内容')).toBeInTheDocument();
  });
});
