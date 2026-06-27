package com.xiaoai.agent.tool.executor.browser;

/**
 * Enum of supported browser actions for Playwright-based automation.
 */
public enum BrowserAction {

    /**
     * Navigate to a specified URL.
     */
    NAVIGATE("Navigate to a specified URL"),

    /**
     * Extract visible text content from the current page.
     */
    GET_TEXT("Extract visible text content from the current page"),

    /**
     * Extract table data from the current page as structured content.
     */
    GET_TABLE("Extract table data from the current page as structured content"),

    /**
     * Capture a screenshot of the current page and return as bytes.
     */
    SCREENSHOT("Capture a screenshot of the current page and return as bytes"),

    /**
     * Fill a form field identified by a CSS selector with a value.
     */
    FILL_FORM("Fill a form field identified by a CSS selector with a value"),

    /**
     * Click an element identified by a CSS selector.
     */
    CLICK_ELEMENT("Click an element identified by a CSS selector"),

    /**
     * Close the browser session and release resources.
     */
    CLOSE("Close the browser session and release resources");

    private final String description;

    BrowserAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
