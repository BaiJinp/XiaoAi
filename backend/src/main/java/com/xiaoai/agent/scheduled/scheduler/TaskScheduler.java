package com.xiaoai.agent.scheduled.scheduler;

import com.xiaoai.agent.scheduled.entity.ScheduledTask;
import com.xiaoai.agent.scheduled.service.ScheduledTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 瀹氭椂浠诲姟璋冨害鍣? * 瀹氭湡妫€鏌ュ苟鎵ц鍒版湡鐨勪换鍔? */
@Component("scheduledTaskExecutor")
public class TaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private final ScheduledTaskService scheduledTaskService;

    @Autowired
    public TaskScheduler(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    /**
     * 姣忓垎閽熸鏌ヤ竴娆″埌鏈熶换鍔?     */
    @Scheduled(fixedRate = 60000) // 60绉?
    public void checkAndExecuteTasks() {
        log.debug("Checking for scheduled tasks to execute...");

        try {
            // 鑾峰彇鎵€鏈夌鎴凤紙杩欓噷绠€鍖栧鐞嗭紝瀹為檯搴旇閬嶅巻鎵€鏈夌鎴凤級
            List<Long> tenantIds = List.of(100L); // TODO: 浠庨厤缃垨鏁版嵁搴撹幏鍙?
            for (Long tenantId : tenantIds) {
                List<ScheduledTask> enabledTasks = scheduledTaskService.getEnabledTasks(tenantId);

                for (ScheduledTask task : enabledTasks) {
                    if (isDueForExecution(task)) {
                        log.info("Executing scheduled task: id={}, code={}, name={}",
                                task.getId(), task.getTaskCode(), task.getTaskName());

                        try {
                            // 鎵ц浠诲姟
                            scheduledTaskService.executeNow(task.getId());

                            // 璁板綍鎴愬姛
                            scheduledTaskService.recordExecution(task.getId(), true);

                            log.info("Task executed successfully: id={}", task.getId());

                        } catch (Exception e) {
                            log.error("Failed to execute task: id={}", task.getId(), e);
                            // 璁板綍澶辫触
                            scheduledTaskService.recordExecution(task.getId(), false);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error in task scheduler", e);
        }
    }

    /**
     * 妫€鏌ヤ换鍔℃槸鍚﹀埌鏈?     */
    private boolean isDueForExecution(ScheduledTask task) {
        if (task.getNextExecutionTime() == null) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now();
        return now.isAfter(task.getNextExecutionTime()) || now.isEqual(task.getNextExecutionTime());
    }
}
