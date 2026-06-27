import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { ChatComposer } from './components/ChatComposer';

describe('ChatComposer', () => {
  it('submits user input and clears composer', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();

    render(<ChatComposer onSubmit={onSubmit} disabled={false} />);

    const input = screen.getByPlaceholderText(/输入项目助理任务/);
    await user.type(input, '生成项目周报');
    await user.click(screen.getByRole('button', { name: /发送/ }));

    expect(onSubmit).toHaveBeenCalledWith('生成项目周报');
    expect(input).toHaveValue('');
  });
});
