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
package org.apache.dubbo.common.threadpool.support.fixed;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadlocal.NamedInternalThreadFactory;
import org.apache.dubbo.common.threadpool.ThreadPool;
import org.apache.dubbo.common.threadpool.support.AbortPolicyWithReport;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_QUEUES;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_THREADS;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_THREAD_NAME;
import static org.apache.dubbo.common.constants.CommonConstants.QUEUES_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.THREADS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.THREAD_NAME_KEY;

/**
 * Creates a thread pool that reuses a fixed number of threads
 *
 * 固定大小线程池，启动时建立线程，不关闭，一直持有
 *
 * 核心线程数：url 参数中，parameter 属性中 key 为 threads 对应的 value
 * 最大线程数：url 参数中，parameter 属性中 key 为 threads 对应的 value
 * 线程存活时间：0
 * 线程存活时间单位：毫秒
 * 阻塞队列：当队列数为 0 时，使用 SynchronousQueue（不存储元素，即队列里最多只有一个元素）；
 *      队列数小于 0 时，使用 LinkedBlockingQueue，队列长度为 Integer.MAX_VALUE；
 *      队列数大于 0 时，使用 LinkedBlockingQueue，队列长度就是队列数 queues
 * 线程工厂：
 * 拒绝策略：dubbo 自定义的拒绝策略，打印报警日志，dumpJStack，抛出异常信息
 *
 * @see java.util.concurrent.Executors#newFixedThreadPool(int)
 */
public class FixedThreadPool implements ThreadPool {

    @Override
    public Executor getExecutor(URL url) {
        // 线程池名
        String name = url.getParameter(THREAD_NAME_KEY, DEFAULT_THREAD_NAME);
        // 线程数
        int threads = url.getParameter(THREADS_KEY, DEFAULT_THREADS);
        // 队列数
        int queues = url.getParameter(QUEUES_KEY, DEFAULT_QUEUES);
        // 创建执行器
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                queues == 0 ? new SynchronousQueue<Runnable>() :
                        (queues < 0 ? new LinkedBlockingQueue<Runnable>()
                                : new LinkedBlockingQueue<Runnable>(queues)),
                new NamedInternalThreadFactory(name, true), new AbortPolicyWithReport(name, url));
    }

}
