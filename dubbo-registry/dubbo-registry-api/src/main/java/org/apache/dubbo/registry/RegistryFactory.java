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
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * RegistryFactory. (SPI, Singleton, ThreadSafe)
 *
 * @see org.apache.dubbo.registry.support.AbstractRegistryFactory
 */
@SPI("dubbo")
public interface RegistryFactory {

    /**
     * Connect to the registry
     * <p>
     * Connecting the registry needs to support the contract: <br>
     * 1. When the check=false is set, the connection is not checked, otherwise the exception is thrown when disconnection <br>
     *     当设置 check=false时，不检查连接，否则在连接不上时抛出异常
     * 2. Support username:password authority authentication on URL.<br>
     *     支持 URL 上的 username:password 权限认证
     * 3. Support the backup=10.20.153.10 candidate registry cluster address.<br>
     *     支持 backup=10.20.153.10 备选注册中心地址
     * 4. Support file=registry.cache local disk file cache.<br>
     *     支持file=registry.cache本地磁盘文件缓存。
     * 5. Support the timeout=1000 request timeout setting.<br>
     *     支持timeout=1000请求超时设置
     * 6. Support session=60000 session timeout or expiration settings.<br>
     *     支持session=60000会话超时或过期设置。
     *
     * @param url Registry address, is not allowed to be empty
     * @return Registry reference, never return empty value
     */
    @Adaptive({"protocol"})
    Registry getRegistry(URL url);

}