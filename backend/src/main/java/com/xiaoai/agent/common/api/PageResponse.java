package com.xiaoai.agent.common.api;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PageResponse<T> {

    private final long pageNo;

    private final long pageSize;

    private final long total;

    private final List<T> records;
}
