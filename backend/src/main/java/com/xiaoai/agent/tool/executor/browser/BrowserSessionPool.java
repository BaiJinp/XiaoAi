package com.xiaoai.agent.tool.executor.browser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a pool of BrowserSession instances with concurrency limiting and auto-cleanup.
 * Maximum 5 concurrent browser sessions allowed. Sessions older than 10 minutes are automatically cleaned up.
 */
@Component
public class BrowserSessionPool {

    private static final Logger log = LoggerFactory.getLogger(BrowserSessionPool.class);

    private static final int MAX_CONCURRENT_SESSIONS = 5;
    private static final Duration SESSION_MAX_AGE = Duration.ofMinutes(10);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final ConcurrentLinkedQueue<BrowserSession> sessionPool = new ConcurrentLinkedQueue<>();
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_SESSIONS);
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private Instant lastCleanupAt = Instant.now();

    /**
     * Acquire a browser session from the pool, or create a new one if available.
     * Blocks if maximum concurrent sessions limit is reached.
     *
     * @return a BrowserSession instance, or null if unable to acquire within timeout
     * @throws InterruptedException if interrupted while waiting
     */
    public BrowserSession acquire() throws InterruptedException {
        log.info("Acquiring browser session (active: {}, max: {})", activeCount.get(), MAX_CONCURRENT_SESSIONS);

        // Perform periodic cleanup of expired sessions
        maybeCleanup();

        // Try to acquire a permit (with timeout to avoid indefinite blocking)
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            log.warn("Maximum concurrent browser sessions limit reached ({})", MAX_CONCURRENT_SESSIONS);
            return null;
        }

        try {
            // Try to reuse an existing inactive session from the pool
            BrowserSession session = sessionPool.poll();
            if (session != null && session.isActive()) {
                log.debug("Reusing existing browser session [id={}]", session.getSessionId());
                activeCount.incrementAndGet();
                return session;
            }

            // Create a new session
            String sessionId = generateSessionId();
            Object page = createPage(); // TODO: Replace with actual Playwright Page creation
            BrowserSession newSession = new BrowserSession(sessionId, page);
            activeCount.incrementAndGet();
            log.info("Created new browser session [id={}] (active: {})", sessionId, activeCount.get());
            return newSession;
        } catch (Exception e) {
            log.error("Failed to create browser session", e);
            semaphore.release();
            throw e;
        }
    }

    /**
     * Release a browser session back to the pool.
     * If the session is still active, it is returned to the pool for reuse.
     * Otherwise, it is closed and discarded.
     *
     * @param session the session to release
     */
    public void release(BrowserSession session) {
        if (session == null) {
            return;
        }

        log.debug("Releasing browser session [id={}]", session.getSessionId());

        if (session.isActive()) {
            // Return to pool for potential reuse
            sessionPool.offer(session);
            log.debug("Returned browser session to pool [id={}]", session.getSessionId());
        } else {
            // Session already closed, just decrement counter
            log.debug("Session already closed, discarding [id={}]", session.getSessionId());
        }

        activeCount.decrementAndGet();
        semaphore.release();
        log.info("Released browser session (active: {})", activeCount.get());
    }

    /**
     * Get the current number of active sessions.
     *
     * @return the number of active sessions
     */
    public int getActiveCount() {
        return activeCount.get();
    }

    /**
     * Cleanup all expired sessions (older than SESSION_MAX_AGE).
     * This method can be called manually or is triggered automatically during acquire().
     */
    public void cleanup() {
        log.info("Starting manual cleanup of expired browser sessions");
        performCleanup();
    }

    /**
     * Perform cleanup if enough time has passed since the last cleanup.
     */
    private void maybeCleanup() {
        Duration elapsed = Duration.between(lastCleanupAt, Instant.now());
        if (elapsed.compareTo(CLEANUP_INTERVAL) >= 0) {
            performCleanup();
            lastCleanupAt = Instant.now();
        }
    }

    /**
     * Perform the actual cleanup of expired sessions.
     */
    private void performCleanup() {
        log.debug("Performing browser session cleanup");
        Instant now = Instant.now();
        int cleaned = 0;

        // Use iterator to safely remove from the queue
        var iterator = sessionPool.iterator();
        while (iterator.hasNext()) {
            BrowserSession session = iterator.next();
            Duration age = Duration.between(session.getCreatedAt(), now);
            if (age.compareTo(SESSION_MAX_AGE) > 0 || !session.isActive()) {
                log.debug("Removing expired session [id={}, age={}s]",
                        session.getSessionId(), age.getSeconds());
                iterator.remove();
                try {
                    session.close();
                    cleaned++;
                } catch (Exception e) {
                    log.warn("Failed to close expired session [id={}]", session.getSessionId(), e);
                }
            }
        }

        if (cleaned > 0) {
            log.info("Cleaned up {} expired browser session(s)", cleaned);
        }
    }

    /**
     * Generate a unique session ID.
     *
     * @return a unique session identifier string
     */
    private String generateSessionId() {
        return "browser-" + System.currentTimeMillis() + "-" +
                Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
    }

    /**
     * Create a new Playwright Page instance.
     * TODO: Implement actual Playwright page creation when dependency is added.
     *
     * @return a Playwright Page object
     */
    private Object createPage() {
        // TODO: Replace with actual Playwright initialization
        // Playwright playwright = Playwright.create();
        // Browser browser = playwright.chromium().launch();
        // return browser.newPage();
        return null;
    }
}
