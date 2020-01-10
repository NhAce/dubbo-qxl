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
package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;

import java.util.List;

/**
 * 注册中心服务接口
 * RegistryService. (SPI, Prototype, ThreadSafe)
 *
 * @see org.apache.dubbo.registry.Registry
 * @see org.apache.dubbo.registry.RegistryFactory#getRegistry(URL)
 */
public interface RegistryService {

    /**
     * Register data, such as : provider service, consumer address, route rule, override rule and other data.
     *  注册数据，比如提供者地址，消费者地址，路由规则，覆盖规则等
     *
     * <p>
     * Registering is required to support the contract:<br>
     *     注册要满足一下规则
     * 1. When the URL sets the check=false parameter. When the registration fails, the exception is not thrown and retried in the background. Otherwise, the exception will be thrown.<br>
     *     当 URL 中配置了 check=false 时，注册失败后不会抛出异常，而是会在后台重试；否则就抛出异常
     * 2. When URL sets the dynamic=false parameter, it needs to be stored persistently, otherwise, it should be deleted automatically when the registrant has an abnormal exit.<br>
     *     当 URL 中配置了 dynamic=false，URL 信息会被持久化，否则，当注册发生异常退出后，url 信息就会被删除
     * 3. When the URL sets category=routers, it means classified storage, the default category is providers, and the data can be notified by the classified section. <br>
     *     当 URL 中配置了 category=routers，表示分类存储，默认类别是 providers(服务提供者列表)【还有 consumers(服务消费者列表)，routers(路由规则列表)，configurations(配置规则列表)】
     * 4. When the registry is restarted, network jitter, data can not be lost, including automatically deleting data from the broken line.<br>
     *     当注册中心重启或者网络抖动时，数据不会丢失
     * 5. Allow URLs which have the same URL but different parameters to coexist,they can't cover each other.<br>
     *     允许注册路径相同但参数不同的 URL 重复注册，且彼此之间不会覆盖
     *
     * @param url  Registration information , is not allowed to be empty, e.g: dubbo://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     */
    void register(URL url);

    /**
     * Unregister
     * 取消注册
     *
     * <p>
     * Unregistering is required to support the contract:<br>
     * 1. If it is the persistent stored data of dynamic=false, the registration data can not be found, then the IllegalStateException is thrown, otherwise it is ignored.<br>
     *     当 url 中配置了 dynamic=false，且 url 对应的注册信息未找到，则抛出 IllegalStateException，否则忽视
     * 2. Unregister according to the full url match.<br>
     *     根据完整的 url 匹配取消注册
     *
     * @param url Registration information , is not allowed to be empty, e.g: dubbo://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     */
    void unregister(URL url);

    /**
     * Subscribe to eligible registered data and automatically push when the registered data is changed.
     * 订阅符合条件的注册信息，并在注册信息修改时自动推送
     *
     * <p>
     * Subscribing need to support contracts:<br>
     * 1. When the URL sets the check=false parameter. When the registration fails, the exception is not thrown and retried in the background. <br>
     *     当 url 中配置了 check=false，当注册失败时，不会抛出异常，而是在后台重试
     * 2. When URL sets category=routers, it only notifies the specified classification data. Multiple classifications are separated by commas, and allows asterisk to match, which indicates that all categorical data are subscribed.<br>
     *     当 url 中配置了 category=routers 时，只会通知指定的分类数据，多个分类用 , 分割，并允许用 * 匹配，这表示订阅了所有分类数据
     * 3. Allow interface, group, version, and classifier as a conditional query, e.g.: interface=org.apache.dubbo.foo.BarService&version=1.0.0<br>
     *     允许将接口，组，版本，分类器作为条件查询
     * 4. And the query conditions allow the asterisk to be matched, subscribe to all versions of all the packets of all interfaces, e.g. :interface=*&group=*&version=*&classifier=*<br>
     *     查询条件允许 * 匹配，这表示订阅了所有包的所有接口
     * 5. When the registry is restarted and network jitter, it is necessary to automatically restore the subscription request.<br>
     *     当注册中心重启或者网络发生抖动时，必须自动恢复订阅请求
     * 6. Allow URLs which have the same URL but different parameters to coexist,they can't cover each other.<br>
     *     允许URL具有相同的URL但不同的参数共存，它们不能互相覆盖
     * 7. The subscription process must be blocked, when the first notice is finished and then returned.<br>
     *     当第一个通知完成并返回时，必须暂停订阅过程
     *
     * @param url      Subscription condition, not allowed to be empty, e.g. consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @param listener A listener of the change event, not allowed to be empty
     */
    void subscribe(URL url, NotifyListener listener);

    /**
     * Unsubscribe
     * 取消订阅
     * <p>
     * Unsubscribing is required to support the contract:<br>
     * 1. If don't subscribe, ignore it directly.<br>
     *     未注册，直接忽略
     * 2. Unsubscribe by full URL match.<br>
     *     根据 url 匹配来取消订阅
     *
     * @param url      Subscription condition, not allowed to be empty, e.g. consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @param listener A listener of the change event, not allowed to be empty
     */
    void unsubscribe(URL url, NotifyListener listener);

    /**
     * Query the registered data that matches the conditions. Corresponding to the push mode of the subscription, this is the pull mode and returns only one result.
     * 根据条件查询已注册的数据，与订阅的推模式相对应，这里为拉模式，只返回一次结果
     *
     * @param url Query condition, is not allowed to be empty, e.g. consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @return The registered information list, which may be empty, the meaning is the same as the parameters of {@link org.apache.dubbo.registry.NotifyListener#notify(List<URL>)}.
     * @see org.apache.dubbo.registry.NotifyListener#notify(List)
     */
    List<URL> lookup(URL url);

}