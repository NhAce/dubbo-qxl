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
package org.apache.dubbo.registry.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.registry.integration.RegistryDirectory;
import org.apache.dubbo.rpc.Invoker;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @date 2017/11/23
 * 服务提供者和消费者注册表，存储 JVM 进程内自己的服务提供者和消费者的 Invoker
 */
public class ProviderConsumerRegTable {
    /**
     *  服务提供者 Invoker 集合
     */
    public static ConcurrentHashMap<String, ConcurrentMap<Invoker, ProviderInvokerWrapper>> providerInvokers = new ConcurrentHashMap<>();
    /**
     * 服务消费者 Invoker 集合
     */
    public static ConcurrentHashMap<String, Set<ConsumerInvokerWrapper>> consumerInvokers = new ConcurrentHashMap<>();

    /**
     * 注册 Provider Invoker
     *
     * @param invoker invoker 对象
     * @param registryUrl 注册中心 url
     * @param providerUrl 服务提供者 url
     * @param <T>
     * @return
     */
    public static <T> ProviderInvokerWrapper<T> registerProvider(Invoker<T> invoker, URL registryUrl, URL providerUrl) {
        // 把 invoker 包装成 ProviderInvokerWrapper
        ProviderInvokerWrapper<T> wrapperInvoker = new ProviderInvokerWrapper<>(invoker, registryUrl, providerUrl);
        // 获取服务键
        String serviceUniqueName = providerUrl.getServiceKey();
        // 添加到集合
        ConcurrentMap<Invoker, ProviderInvokerWrapper> invokers = providerInvokers.get(serviceUniqueName);
        if (invokers == null) {
            providerInvokers.putIfAbsent(serviceUniqueName, new ConcurrentHashMap<>());
            invokers = providerInvokers.get(serviceUniqueName);
        }
        invokers.put(invoker, wrapperInvoker);
        return wrapperInvoker;
    }

    /*public static ProviderInvokerWrapper removeProviderWrapper(Invoker invoker, URL providerUrl) {
        String serviceUniqueName = providerUrl.getServiceKey();
        Set<ProviderInvokerWrapper> invokers = providerInvokers.get(serviceUniqueName);
        if (invokers == null) {
            return null;
        }
        return invokers.remove(new ProviderIndvokerWrapper(invoker, null, null));
    }*/

    /**
     * 获取指定服务键的 Provider Invoker 集合
     * @param serviceUniqueName
     * @return
     */
    public static Set<ProviderInvokerWrapper> getProviderInvoker(String serviceUniqueName) {
        ConcurrentMap<Invoker, ProviderInvokerWrapper> invokers = providerInvokers.get(serviceUniqueName);
        if (invokers == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(invokers.values());
    }

    /**
     * 获取服务提供者对应的 Invoker Wrapper 对象
     *
     * @param registeredProviderUrl 服务提供者 url
     * @param invoker 服务提供者 Invoker
     * @param <T>
     * @return
     */
    public static <T> ProviderInvokerWrapper<T> getProviderWrapper(URL registeredProviderUrl, Invoker<T> invoker) {
        String serviceUniqueName = registeredProviderUrl.getServiceKey();
        ConcurrentMap<Invoker, ProviderInvokerWrapper> invokers = providerInvokers.get(serviceUniqueName);
        if (invokers == null) {
            return null;
        }

        for (Map.Entry<Invoker, ProviderInvokerWrapper> entry : invokers.entrySet()) {
            if (entry.getKey() == invoker) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 注册 Consumer Invoker
     *
     * @param invoker invoker 对象
     * @param registryUrl 注册中心 url
     * @param consumerUrl 服务消费者 url
     * @param registryDirectory 注册中心 directory
     */
    public static void registerConsumer(Invoker invoker, URL registryUrl, URL consumerUrl, RegistryDirectory registryDirectory) {
        // 创建 ConsumerInvokerWrapper 对象
        ConsumerInvokerWrapper wrapperInvoker = new ConsumerInvokerWrapper(invoker, registryUrl, consumerUrl, registryDirectory);
        // 服务键
        String serviceUniqueName = consumerUrl.getServiceKey();
        // 添加到集合
        Set<ConsumerInvokerWrapper> invokers = consumerInvokers.get(serviceUniqueName);
        if (invokers == null) {
            consumerInvokers.putIfAbsent(serviceUniqueName, new ConcurrentHashSet<ConsumerInvokerWrapper>());
            invokers = consumerInvokers.get(serviceUniqueName);
        }
        invokers.add(wrapperInvoker);
    }

    /**
     * 获取指定服务键的 Consumer Invoker 对象集合
     *
     * @param serviceUniqueName
     * @return
     */
    public static Set<ConsumerInvokerWrapper> getConsumerInvoker(String serviceUniqueName) {
        Set<ConsumerInvokerWrapper> invokers = consumerInvokers.get(serviceUniqueName);
        return invokers == null ? Collections.emptySet() : invokers;
    }

    /**
     * 判断指定服务键对应的 ProviderInvokerWrapper 中是否有已注册的
     * @param serviceUniqueName
     * @return
     */
    public static boolean isRegistered(String serviceUniqueName) {
        Set<ProviderInvokerWrapper> providerInvokerWrapperSet = ProviderConsumerRegTable.getProviderInvoker(serviceUniqueName);
        return providerInvokerWrapperSet.stream().anyMatch(ProviderInvokerWrapper::isReg);
    }

    public static int getConsumerAddressNum(String serviceUniqueName) {
        Set<ConsumerInvokerWrapper> providerInvokerWrapperSet = ProviderConsumerRegTable.getConsumerInvoker(serviceUniqueName);
        return providerInvokerWrapperSet.stream()
                .map(w -> w.getRegistryDirectory().getUrlInvokerMap())
                .filter(Objects::nonNull)
                .mapToInt(Map::size).sum();
    }
}
