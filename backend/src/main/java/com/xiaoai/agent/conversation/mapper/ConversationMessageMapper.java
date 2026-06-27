package com.xiaoai.agent.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaoai.agent.conversation.entity.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {
}
