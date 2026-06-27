import { create } from 'zustand';
import type { ApprovalRequest } from '../../types/approval';

export type ChatMessageRole = 'user' | 'assistant' | 'system';

export interface ChatMessageLink {
  label: string;
  href: string;
}

export interface ChatMessage {
  id: string;
  role: ChatMessageRole;
  content: string;
  createTime?: string;
  approval?: ApprovalRequest;
  link?: ChatMessageLink;
}

interface ChatState {
  messages: ChatMessage[];
  addMessage: (message: Omit<ChatMessage, 'id' | 'createTime'>) => ChatMessage;
  clearMessages: () => void;
}

function createMessageId() {
  return `message-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export const useChatStore = create<ChatState>((set) => ({
  messages: [],
  addMessage: (message) => {
    const nextMessage: ChatMessage = {
      ...message,
      id: createMessageId(),
      createTime: new Date().toISOString(),
    };
    set((state) => ({ messages: [...state.messages, nextMessage] }));
    return nextMessage;
  },
  clearMessages: () => set({ messages: [] }),
}));
