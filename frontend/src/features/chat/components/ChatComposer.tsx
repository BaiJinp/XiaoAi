import { SendOutlined } from '@ant-design/icons';
import { Button, Input, Space } from 'antd';
import { useState } from 'react';

interface ChatComposerProps {
  disabled?: boolean;
  draft?: string;
  onDraftChange?: (content: string) => void;
  onSubmit: (content: string) => void;
}

export function ChatComposer({ disabled = false, draft, onDraftChange, onSubmit }: ChatComposerProps) {
  const [innerContent, setInnerContent] = useState('');
  const content = draft ?? innerContent;
  const updateContent = (nextContent: string) => {
    if (draft === undefined) {
      setInnerContent(nextContent);
    }
    onDraftChange?.(nextContent);
  };

  const submit = () => {
    const normalizedContent = content.trim();
    if (!normalizedContent || disabled) {
      return;
    }
    onSubmit(normalizedContent);
    updateContent('');
  };

  return (
    <Space.Compact style={{ width: '100%' }}>
      <Input.TextArea
        autoSize={{ minRows: 3, maxRows: 6 }}
        disabled={disabled}
        onChange={(event) => updateContent(event.target.value)}
        onPressEnter={(event) => {
          if (!event.shiftKey) {
            event.preventDefault();
            submit();
          }
        }}
        placeholder="输入项目助理任务，例如：请根据以下会议纪要提取行动项并生成任务草稿..."
        value={content}
      />
      <Button
        type="primary"
        disabled={disabled || !content.trim()}
        icon={<SendOutlined />}
        onClick={submit}
        style={{ height: 'auto' }}
      >
        发送
      </Button>
    </Space.Compact>
  );
}
