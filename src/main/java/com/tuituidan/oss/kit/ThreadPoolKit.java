package com.tuituidan.oss.kit;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

/**
 * ThreadPoolKit.
 *
 * @author zhujunhan
 * @version 1.0
 * @date 2020/8/9
 */
@UtilityClass
public class ThreadPoolKit {

    /**
     * 任务等待队列 容量.
     */
    private static final int TASK_QUEUE_SIZE = 1000;
    /**
     * 空闲线程存活时间 单位分钟.
     */
    private static final long KEEP_ALIVE_TIME = 10L;

    /**
     * 任务执行线程池.
     */
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR;

    static {
        int corePoolNum = 2 * Runtime.getRuntime().availableProcessors() + 1;
        int maximumPoolSize = 2 * corePoolNum;
        THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(corePoolNum, maximumPoolSize, KEEP_ALIVE_TIME, TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(TASK_QUEUE_SIZE),
                new BasicThreadFactory.Builder().namingPattern("image-host-%d").build(), (r, executor) -> {
            if (!executor.isShutdown()) {
                try {
                    executor.getQueue().put(r);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    /**
     * 添加任务到线程池.
     *
     * @param task 执行任务
     */
    public static void execute(Runnable task) {
        THREAD_POOL_EXECUTOR.execute(task);
    }
}
