package com.xiaoai.agent.tool.executor.browser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a single browser page session backed by Playwright.
 * Thread-safe through ReentrantLock to prevent concurrent access to the same page.
 */
public class BrowserSession {

    private static final Logger log = LoggerFactory.getLogger(BrowserSession.class);

    private final String sessionId;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * The Playwright Page instance. Stored as Object since Playwright is not yet in pom.xml.
     * Cast to Page when Playwright dependency is added.
     */
    private Object page;

    private Instant createdAt;
    private Instant lastAccessedAt;
    private String url;
    private boolean isActive;

    public BrowserSession(String sessionId, Object page) {
        this.sessionId = sessionId;
        this.page = page;
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
        this.isActive = true;
        log.debug("BrowserSession created with id: {}", sessionId);
    }

    /**
     * Navigate to the specified URL.
     *
     * @param url the URL to navigate to
     * @return the result of the navigation action
     */
    public BrowserActionResult navigate(String url) {
        long start = System.currentTimeMillis();
        lock.lock();
        try {
            validateActive();
            log.info("Navigating to URL: {} [session={}]", url, sessionId);
            this.url = url;
            this.lastAccessedAt = Instant.now();
            // TODO: Implement actual Playwright page navigation when dependency is available
            // page.navigate(url);
            return BrowserActionResult.builder()
                    .success(true)
                    .action(BrowserAction.NAVIGATE)
                    .content("Navigated to " + url)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("Failed to navigate to URL: {} [session={}]", url, sessionId, e);
            return BrowserActionResult.builder()
                    .success(false)
                    .action(BrowserAction.NAVIGATE)
                    .errorMessage(e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Extract visible text content from the current page.
     *
     * @return the extracted text content
     */
    public BrowserActionResult getTextContent() {
        long start = System.currentTimeMillis();
        lock.lock();
        try {
            validateActive();
            log.info("Extracting text content [session={}]", sessionId);
            this.lastAccessedAt = Instant.now();
            // TODO: Implement actual Playwright text extraction
            // String text = page.textContent("body");
            String text = "";
            return BrowserActionResult.builder()
                    .success(true)
                    .action(BrowserAction.GET_TEXT)
                    .content(text)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("Failed to extract text content [session={}]", sessionId, e);
            return BrowserActionResult.builder()
                    .success(false)
                    .action(BrowserAction.GET_TEXT)
                    .errorMessage(e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Extract table data from the current page.
     *
     * @return the extracted table content as JSON string
     */
    public BrowserActionResult getTableContent() {
        long start = System.currentTimeMillis();
        lock.lock();
        try {
            validateActive();
            log.info("Extracting table content [session={}]", sessionId);
            this.lastAccessedAt = Instant.now();
            // TODO: Implement actual Playwright table extraction
            // String tableJson = page.evaluate("...table extraction logic...");
            String tableJson = "[]";
            return BrowserActionResult.builder()
                    .success(true)
                    .action(BrowserAction.GET_TABLE)
                    .content(tableJson)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("Failed to extract table content [session={}]", sessionId, e);
            return BrowserActionResult.builder()
                    .success(false)
                    .action(BrowserAction.GET_TABLE)
                    .errorMessage(e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take a screenshot of the current page.
     *
     * @return screenshot as byte array
     */
    public BrowserActionResult takeScreenshot() {
        long start = System.currentTimeMillis();
        lock.lock();
        try {
            validateActive();
            log.info("Taking screenshot [session={}]", sessionId);
            this.lastAccessedAt = Instant.now();
            // TODO: Implement actual Playwright screenshot capture
            // byte[] bytes = page.screenshot();
            byte[] bytes = new byte[0];
            return BrowserActionResult.builder()
                    .success(true)
                    .action(BrowserAction.SCREENSHOT)
                    .contentBytes(bytes)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("Failed to take screenshot [session={}]", sessionId, e);
            return BrowserActionResult.builder()
                    .success(false)
                    .action(BrowserAction.SCREENSHOT)
                    .errorMessage(e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fill a form field identified by a CSS selector with the given value.
     *
     * @param selector CSS selector for the form field
     * @param value    the value to fill
     * @return the result of the fill action
     */
    public BrowserActionResult fillForm(String selector, String value) {
        long start = System.currentTimeMillis();
        lock.lock();
        try {
            validateActive();
            log.info("Filling form field: selector={}, value=[REDACTED] [session={}]", selector, sessionId);
            this.lastAccessedAt = Instant.now();
            // TODO: Implement actual Playwright form filling
            // page.fill(selector, value);
            return BrowserActionResult.builder()
                    .success(true)
                    .action(BrowserAction.FILL_FORM)
                    .content("Filled field: " + selector)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("Failed to fill form field: selector={} [session={}]", selector, sessionId, e);
            return BrowserActionResult.builder()
                    .success(false)
                    .action(BrowserAction.FILL_FORM)
                    .errorMessage(e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Click an element identified by a CSS selector.
     *
     * @param selector CSS selector for the element to click
     * @return the result of the click action
     */
    public BrowserActionResult clickElement(String selector) {
        long start = System.currentTimeMillis();
        lock.lock();
        try {
            validateActive();
            log.info("Clicking element: selector={} [session={}]", selector, sessionId);
            this.lastAccessedAt = Instant.now();
            // TODO: Implement actual Playwright element clicking
            // page.click(selector);
            return BrowserActionResult.builder()
                    .success(true)
                    .action(BrowserAction.CLICK_ELEMENT)
                    .content("Clicked element: " + selector)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("Failed to click element: selector={} [session={}]", selector, sessionId, e);
            return BrowserActionResult.builder()
                    .success(false)
                    .action(BrowserAction.CLICK_ELEMENT)
                    .errorMessage(e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Close the browser session and release resources.
     *
     * @return the result of the close action
     */
    public BrowserActionResult close() {
        long start = System.currentTimeMillis();
        lock.lock();
        try {
            if (!isActive) {
                return BrowserActionResult.builder()
                        .success(true)
                        .action(BrowserAction.CLOSE)
                        .content("Session already closed")
                        .elapsedMs(System.currentTimeMillis() - start)
                        .build();
            }
            log.info("Closing browser session [session={}]", sessionId);
            isActive = false;
            this.lastAccessedAt = Instant.now();
            // TODO: Implement actual Playwright page/browser closing
            // if (page != null) {
            //     ((Page) page).close();
            // }
            return BrowserActionResult.builder()
                    .success(true)
                    .action(BrowserAction.CLOSE)
                    .content("Session closed: " + sessionId)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("Failed to close browser session [session={}]", sessionId, e);
            return BrowserActionResult.builder()
                    .success(false)
                    .action(BrowserAction.CLOSE)
                    .errorMessage(e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        } finally {
            lock.unlock();
        }
    }

    // Getters

    public String getSessionId() {
        return sessionId;
    }

    public Object getPage() {
        return page;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public String getUrl() {
        return url;
    }

    public boolean isActive() {
        return isActive;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    private void validateActive() {
        if (!isActive) {
            throw new IllegalStateException("Browser session is closed: " + sessionId);
        }
    }
}
