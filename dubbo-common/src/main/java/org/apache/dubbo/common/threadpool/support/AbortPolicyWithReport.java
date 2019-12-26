/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.threadpool.support;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.JVMUtil;

import static org.apache.dubbo.common.constants.CommonConstants.DUMP_DIRECTORY;

/**
 * Abort Policy.
 * Log warn info when abort.
 *
 * 拒绝策略实现类。打印 JStack，分析线程状态
 *
 */
public class AbortPolicyWithReport extends ThreadPoolExecutor.AbortPolicy {

    protected static final Logger logger = LoggerFactory.getLogger(AbortPolicyWithReport.class);

    /**
     * 线程名
     */
    private final String threadName;

    /**
     * url 对象
     */
    private final URL url;

    /**
     * 最后打印时间
     */
    private static volatile long lastPrintTime = 0;

    private static final long TEN_MINUTES_MILLS = 10 * 60 * 1000;

    private static final String OS_WIN_PREFIX = "win";

    private static final String OS_NAME_KEY = "os.name";

    private static final String WIN_DATETIME_FORMAT = "yyyy-MM-dd_HH-mm-ss";

    private static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    private static Semaphore guard = new Semaphore(1);

    public AbortPolicyWithReport(String threadName, URL url) {
        this.threadName = threadName;
        this.url = url;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        String msg = String.format("Thread pool is EXHAUSTED!" +
                " Thread Name: %s, Pool Size: %d (active: %d, core: %d, max: %d, largest: %d), Task: %d (completed: "
                + "%d)," +
                " Executor status:(isShutdown:%s, isTerminated:%s, isTerminating:%s), in %s://%s:%d!",
            threadName, e.getPoolSize(), e.getActiveCount(), e.getCorePoolSize(), e.getMaximumPoolSize(),
            e.getLargestPoolSize(),
            e.getTaskCount(), e.getCompletedTaskCount(), e.isShutdown(), e.isTerminated(), e.isTerminating(),
            url.getProtocol(), url.getIp(), url.getPort());
        // 打印预警日志
        logger.warn(msg);
        // 打印 JStack，分析线程状态
        dumpJStack();
        // 抛出异常
        throw new RejectedExecutionException(msg);
    }

    private void dumpJStack() {
        long now = System.currentTimeMillis();

        //dump every 10 minutes
        // 每 10 分钟，打印一次
        if (now - lastPrintTime < TEN_MINUTES_MILLS) {
            return;
        }

        // 获取信号量
        if (!guard.tryAcquire()) {
            return;
        }

        // 创建线程池，后台执行打印 JStack 的任务
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            // 获得 dump 文件存储的路径
            String dumpPath = url.getParameter(DUMP_DIRECTORY, System.getProperty("user.home"));

            SimpleDateFormat sdf;

            // 获得系统名称
            String os = System.getProperty(OS_NAME_KEY).toLowerCase();

            // window system don't support ":" in file name
            if (os.contains(OS_WIN_PREFIX)) {
                sdf = new SimpleDateFormat(WIN_DATETIME_FORMAT);
            } else {
                sdf = new SimpleDateFormat(DEFAULT_DATETIME_FORMAT);
            }

            String dateStr = sdf.format(new Date());
            //try-with-resources
            //获得输出流
            try (FileOutputStream jStackStream = new FileOutputStream(
                new File(dumpPath, "Dubbo_JStack.log" + "." + dateStr))) {
                // 打印 JStack
                JVMUtil.jstack(jStackStream);
            } catch (Throwable t) {
                logger.error("dump jStack error", t);
            } finally {
                // 释放信号量
                guard.release();
            }
            // 记录最后打印时间
            lastPrintTime = System.currentTimeMillis();
        });
        //must shutdown thread pool ,if not will lead to OOM
        // 关闭线程池，否则会出现 OOM
        pool.shutdown();

    }

}
