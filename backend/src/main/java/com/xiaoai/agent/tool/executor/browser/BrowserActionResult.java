package com.xiaoai.agent.tool.executor.browser;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Result object returned by browser actions.
 */
@Getter
@Setter
@Builder
public class BrowserActionResult {

    private boolean success;

    private BrowserAction action;

    private String content;

    private byte[] contentBytes;

    private String errorMessage;

    private long elapsedMs;
}
