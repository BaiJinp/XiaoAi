package com.xiaoai.agent.runtime.controller;

import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.runtime.entity.RuntimeRunSnapshot;
import com.xiaoai.agent.runtime.service.RuntimeRunSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/runtime-run-snapshots")
public class RuntimeRunSnapshotController {

    private final RuntimeRunSnapshotService runtimeRunSnapshotService;

    @GetMapping("/{id}")
public ApiResponse<RuntimeRunSnapshot> getById(@PathVariable Long id) {
        return ApiResponse.success(runtimeRunSnapshotService.getById(id));
    }
}
