package com.xiaoai.agent.common.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageQuery {

    private long pageNo = 1;

    private long pageSize = 20;
public long normalizedPageNo() {
        return pageNo <= 0 ? 1 : pageNo;
    }
public long normalizedPageSize() {
        if (pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
