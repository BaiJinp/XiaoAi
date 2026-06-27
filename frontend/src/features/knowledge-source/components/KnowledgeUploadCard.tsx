import { Button, Card, Flex, Form, Input, InputNumber, Space, Tag, Typography, message } from 'antd';
import { useState } from 'react';
import type {
  IngestKnowledgeTextRequest,
  KnowledgeIngestResponse,
  KnowledgeRetrieveResult,
  RetrieveKnowledgeRequest,
} from '../../../services/knowledge-api';
import { ingestKnowledgeText, retrieveKnowledge } from '../../../services/knowledge-api';

interface KnowledgeUploadCardProps {
  onIngest?: (request: IngestKnowledgeTextRequest) => Promise<KnowledgeIngestResponse>;
  onRetrieve?: (request: RetrieveKnowledgeRequest) => Promise<KnowledgeRetrieveResult[]>;
}

interface IngestFormValues {
  knowledgeBaseId: number;
  title: string;
  content: string;
}

interface RetrieveFormValues {
  knowledgeBaseId: number;
  query: string;
}

const confidenceColor: Record<string, string> = {
  high: 'green',
  medium: 'blue',
  low: 'orange',
  conflicting: 'red',
  insufficient: 'default',
};

function confidenceLabel(confidence?: string) {
  if (!confidence) {
    return '未评估';
  }
  const labels: Record<string, string> = {
    high: '高可信',
    medium: '中可信',
    low: '低可信',
    conflicting: '来源冲突',
    insufficient: '依据不足',
  };
  return labels[confidence] || confidence;
}

export function KnowledgeUploadCard({
  onIngest = ingestKnowledgeText,
  onRetrieve = retrieveKnowledge,
}: KnowledgeUploadCardProps) {
  const [ingesting, setIngesting] = useState(false);
  const [retrieving, setRetrieving] = useState(false);
  const [ingestResult, setIngestResult] = useState<KnowledgeIngestResponse>();
  const [retrieveResults, setRetrieveResults] = useState<KnowledgeRetrieveResult[]>([]);
  const [messageApi, contextHolder] = message.useMessage();

  const handleIngest = async (values: IngestFormValues) => {
    setIngesting(true);
    try {
      const result = await onIngest(values);
      setIngestResult(result);
      messageApi.success('提交成功');
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '项目资料入库失败');
    } finally {
      setIngesting(false);
    }
  };

  const handleRetrieve = async (values: RetrieveFormValues) => {
    setRetrieving(true);
    try {
      const results = await onRetrieve({ ...values, topK: 5 });
      setRetrieveResults(results);
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '知识检索失败');
    } finally {
      setRetrieving(false);
    }
  };

  return (
    <Flex vertical gap={16} style={{ width: '100%' }}>
      {contextHolder}
      <Card title="项目资料入库" variant="borderless">
        <Form name="knowledge-ingest" layout="vertical" initialValues={{ knowledgeBaseId: 1 }} onFinish={handleIngest}>
          <Form.Item label="知识库 ID" name="knowledgeBaseId" rules={[{ required: true, message: '请输入知识库 ID' }]}>
            <InputNumber min={1} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item label="文档标题" name="title" rules={[{ required: true, message: '请输入文档标题' }]}>
            <Input placeholder="例如：本周项目会议纪要" />
          </Form.Item>
          <Form.Item label="文档文本内容" name="content" rules={[{ required: true, message: '请输入资料内容' }]}>
            <Input.TextArea rows={8} placeholder="粘贴项目资料、会议纪要或周报原文" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={ingesting}>
            提交入库
          </Button>
        </Form>
        {ingestResult ? (
          <Typography.Paragraph style={{ marginTop: 16 }}>
            已入库文档 #{ingestResult.documentId}，切分 {ingestResult.chunkCount} 个片段。
          </Typography.Paragraph>
        ) : null}
      </Card>

      <Card title="检索调试" variant="borderless">
        <Form name="knowledge-retrieve" layout="vertical" initialValues={{ knowledgeBaseId: 1 }} onFinish={handleRetrieve}>
          <Form.Item label="检索知识库 ID" name="knowledgeBaseId" rules={[{ required: true, message: '请输入知识库 ID' }]}>
            <InputNumber min={1} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item label="检索关键词" name="query" rules={[{ required: true, message: '请输入检索关键词' }]}>
            <Input placeholder="输入关键词，例如：延期风险" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={retrieving}>
            检索资料
          </Button>
        </Form>
        {retrieveResults.length > 0 ? (
          <Flex vertical gap={12} style={{ marginTop: 16 }}>
            {retrieveResults.map((item) => (
              <Card key={item.chunkId} size="small" title={item.sourceTitle || `文档 #${item.documentId}`}>
                <Space wrap size={8} style={{ marginBottom: 8 }}>
                  <Tag color={confidenceColor[item.confidence || ''] || 'default'}>{confidenceLabel(item.confidence)}</Tag>
                  {item.sourceType ? <Tag>{item.sourceType}</Tag> : null}
                  {item.accessChecked ? <Tag color="green">已校验权限</Tag> : <Tag>权限未标记</Tag>}
                </Space>
                <Typography.Paragraph style={{ marginBottom: 8 }}>{item.snippet || item.content}</Typography.Paragraph>
                <Typography.Text type="secondary">
                  片段 #{item.chunkIndex ?? item.chunkId} {typeof item.score === 'number' ? ` · 得分 ${item.score}` : ''}
                </Typography.Text>
              </Card>
            ))}
          </Flex>
        ) : (
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 16 }}>
            暂无检索结果
          </Typography.Text>
        )}
      </Card>
    </Flex>
  );
}
