import { beforeEach, describe, expect, it } from 'vitest';
import { useChatStore } from './chat-store';

describe('useChatStore', () => {
  beforeEach(() => {
    useChatStore.getState().clearMessages();
  });

  it('adds user messages to the timeline', () => {
    const message = useChatStore.getState().addMessage({
      role: 'user',
      content: '生成项目周报',
    });

    expect(message.id).toMatch(/^message-/);
    expect(message.role).toBe('user');
    expect(message.content).toBe('生成项目周报');
    expect(useChatStore.getState().messages).toEqual([message]);
  });
});
