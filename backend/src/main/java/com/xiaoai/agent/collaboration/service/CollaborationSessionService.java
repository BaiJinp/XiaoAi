package com.xiaoai.agent.collaboration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.model.CollaborationSessionPageQuery;
import com.xiaoai.agent.collaboration.model.CollaborationSessionResponse;
import com.xiaoai.agent.collaboration.model.CreateCollaborationSessionCommand;
import com.xiaoai.agent.common.api.PageResponse;

public interface CollaborationSessionService extends IService<CollaborationSession> {

    CollaborationSessionResponse createSession(CreateCollaborationSessionCommand command);

    CollaborationSession getSession(Long sessionId);

    PageResponse<CollaborationSession> pageSessions(CollaborationSessionPageQuery query);
}
