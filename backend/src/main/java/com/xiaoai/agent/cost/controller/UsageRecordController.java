package com.xiaoai.agent.cost.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.cost.entity.UsageRecord;
import com.xiaoai.agent.cost.service.UsageRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/usage-records")
public class UsageRecordController {

    private final UsageRecordService usageRecordService;

    @GetMapping("/{id}")
public ApiResponse<UsageRecord> getById(@PathVariable Long id) {
        return ApiResponse.success(usageRecordService.getById(id));
    }
}
