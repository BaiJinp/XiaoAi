package com.xiaoai.agent.task.service;

import com.xiaoai.agent.runtime.model.RuntimeEvent;

public interface TaskEventRecordService {

    void record(RuntimeEvent event);
}
