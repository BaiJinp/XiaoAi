/**
 * SSE (Server-Sent Events) 客户端
 * 用于接收 Agent 执行的实时事件流
 */
export class StreamingClient {
  private sessionId: string | null = null;
  private eventSource: EventSource | null = null;
  private onText: ((text: string) => void) | null = null;
  private onProgress: ((phase: string, message: string, percent: number | null) => void) | null = null;
  private onTool: ((tool: string, status: string, result: string) => void) | null = null;
  private onComplete: ((summary: string) => void) | null = null;
  private onError: ((message: string) => void) | null = null;

  /**
   * 创建新的流式会话
   */
  async createSession(): Promise<string> {
    const response = await fetch('/api/v1/streaming/sessions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    const data = await response.json();
    this.sessionId = data.data.sessionId;
    return this.sessionId;
  }

  /**
   * 开始接收事件流
   */
  startStream(
    callbacks: {
      onText?: (text: string) => void;
      onProgress?: (phase: string, message: string, percent: number | null) => void;
      onTool?: (tool: string, status: string, result: string) => void;
      onComplete?: (summary: string) => void;
      onError?: (message: string) => void;
    }
  ) {
    if (!this.sessionId) {
      throw new Error('Session not created. Call createSession() first.');
    }

    this.onText = callbacks.onText || null;
    this.onProgress = callbacks.onProgress || null;
    this.onTool = callbacks.onTool || null;
    this.onComplete = callbacks.onComplete || null;
    this.onError = callbacks.onError || null;

    // 创建 EventSource 连接
    this.eventSource = new EventSource(
      `/api/v1/streaming/sessions/${this.sessionId}/events`
    );

    // 监听各种事件类型
    this.eventSource.addEventListener('text', (event) => {
      if (this.onText) {
        this.onText(event.data);
      }
    });

    this.eventSource.addEventListener('progress', (event) => {
      if (this.onProgress) {
        const data = JSON.parse(event.data);
        this.onProgress(data.phase, data.message, data.percent);
      }
    });

    this.eventSource.addEventListener('tool', (event) => {
      if (this.onTool) {
        const data = JSON.parse(event.data);
        this.onTool(data.tool, data.status, data.result);
      }
    });

    this.eventSource.addEventListener('complete', (event) => {
      if (this.onComplete) {
        const data = JSON.parse(event.data);
        this.onComplete(data.summary);
      }
      this.close();
    });

    this.eventSource.addEventListener('error', (event) => {
      if (this.onError) {
        const data = JSON.parse(event.data);
        this.onError(data.message);
      }
    });

    // 连接错误处理
    this.eventSource.onerror = () => {
      if (this.onError) {
        this.onError('Connection error');
      }
      this.close();
    };
  }

  /**
   * 关闭流式连接
   */
  close() {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }

    if (this.sessionId) {
      fetch(`/api/v1/streaming/sessions/${this.sessionId}`, {
        method: 'DELETE',
      });
      this.sessionId = null;
    }
  }

  /**
   * 获取会话 ID
   */
  getSessionId(): string | null {
    return this.sessionId;
  }
}

/**
 * 使用示例
 */
export function exampleUsage() {
  const client = new StreamingClient();

  // 1. 创建会话
  client.createSession().then((sessionId) => {
    console.log('Session created:', sessionId);

    // 2. 开始接收事件流
    client.startStream({
      onText: (text) => {
        console.log('Text:', text);
        // 更新 UI 显示文本
      },
      onProgress: (phase, message, percent) => {
        console.log(`Progress [${phase}]: ${message} (${percent}%)`);
        // 更新进度条
      },
      onTool: (tool, status, result) => {
        console.log(`Tool [${tool}]: ${status}`, result);
        // 显示工具调用状态
      },
      onComplete: (summary) => {
        console.log('Completed:', summary);
        // 显示完成摘要
      },
      onError: (message) => {
        console.error('Error:', message);
        // 显示错误信息
      },
    });

    // 3. 启动 Agent 任务（通过其他 API）
    // fetch('/api/v1/tasks', { method: 'POST', ... });
  });

  // 4. 关闭连接（任务完成或用户取消时）
  // client.close();
}
