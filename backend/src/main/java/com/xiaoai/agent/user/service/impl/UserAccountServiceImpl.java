package com.xiaoai.agent.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.user.entity.UserAccount;
import com.xiaoai.agent.user.mapper.UserAccountMapper;
import com.xiaoai.agent.user.service.UserAccountService;
import org.springframework.stereotype.Service;

@Service
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {
}
