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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.support.ActivateComparator;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOVE_VALUE_PREFIX;

/**
 * Load dubbo extensions
 * <ul>
 * <li>auto inject dependency extension </li>
 * <li>auto wrap extension in wrapper </li>
 * <li>default extension is an adaptive instance</li>
 * </ul>
 *
 * @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jar/jar.html#Service%20Provider">Service Provider in Java 5</a>
 * @see org.apache.dubbo.common.extension.SPI
 * @see org.apache.dubbo.common.extension.Adaptive
 * @see org.apache.dubbo.common.extension.Activate
 */
public class ExtensionLoader<T> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final String SERVICES_DIRECTORY = "META-INF/services/";

    private static final String DUBBO_DIRECTORY = "META-INF/dubbo/";

    private static final String DUBBO_INTERNAL_DIRECTORY = DUBBO_DIRECTORY + "internal/";

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    /**
     * 拓展加载器集合
     *
     * key：拓展接口
     */
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();

    /**
     * 拓展实现类集合
     *
     * key：拓展类对象
     * value：拓展实例
     *
     * 例如：key 为 Class<AccessLogFilter>
     *     value 为 AccessLogFilter 实例
     */
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>();

    // ============================== 对象属性 ========================

    /**
     * 拓展接口
     * 例如，Protocol
     */
    private final Class<?> type;

    /**
     * 对象工厂，
     *
     * 用于调用 {@link #injectExtension(Object) 方法，向拓展对象注入依赖属性}
     *
     * 例如，StubProxyFactoryWrapper 中有 Protocol 属性
     */
    private final ExtensionFactory objectFactory;

    /**
     * 缓存的拓展类对象和拓展名的映射
     *
     * 和 {@link #cachedClasses} 中 map 元素 的 k,v 对调
     *
     * 通过 {@link #loadExtensionClasses()} 加载
     */
    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<>();

    /**
     * 缓存的拓展名和拓展实现类对象的映射
     *
     * 不包含以下两种类型：
     *   1. 自适应拓展实现类。例如 AdaptiveExtensionFactory
     *   2. wrapper 实现类（自身实现了拓展接口 A ，且存在一个构造方法：只有一个参数，且参数类型是拓展接口 A），例如，ProtocolFilterWrapper
     *      拓展的 wrapper 实现类，会添加到 {@link #cachedWrapperClasses} 中
     *
     * 通过 {@link #loadExtensionClasses()} 加载
     */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();
    /**
     * 拓展名与 @Active 的映射
     *
     * 例如，AccessLogFilter
     *
     * 通过 {@link #loadExtensionClasses()} 加载
     *
     * 用于 {@link #getActivateExtension(URL, String)}
     */
    private final Map<String, Object> cachedActivates = new ConcurrentHashMap<>();
    /**
     * 缓存的拓展实例集合
     *
     * key：拓展名
     * value：拓展实例
     *
     * 例如，Protocol 拓展
     *      key: dubbo value: DubboProtocol
     *      key: injvm value: InjvmProtocol
     *
     * 通过 {@link #getExtension(String)} 加载
     */
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();
    /**
     * 缓存的自适应拓展实例
     *
     * 通过 {@link #getAdaptiveExtension()} 赋值
     */
    private final Holder<Object> cachedAdaptiveInstance = new Holder<>();
    /**
     * 缓存的自适应拓展类对象
     *
     * {@link #getAdaptiveExtensionClass()}
     * {@link #loadClass(Map, java.net.URL, Class, String)}
     */
    private volatile Class<?> cachedAdaptiveClass = null;
    /**
     * 缓存的默认拓展名
     *
     * 通过 {@link SPI} 注解获得
     */
    private String cachedDefaultName;
    /**
     * 创建 {@link #cachedAdaptiveInstance} 时发生的异常
     *
     * 发生异常后，不再创建，参见 {@link #getAdaptiveExtension()}
     */
    private volatile Throwable createAdaptiveInstanceError;

    /**
     * 拓展的 wrapper 实现类类对象的集合
     *
     * 通过 {@link #loadExtensionClasses()} 加载
     */
    private Set<Class<?>> cachedWrapperClasses;

    /**
     * 拓展名 与 加载对应拓展类时发生的异常 的映射
     *
     * key：拓展名
     * value：异常
     *
     * 在 {@link #loadResource(Map, ClassLoader, java.net.URL)} 报错时记录
     */
    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<>();

    private ExtensionLoader(Class<?> type) {
        this.type = type;
        // 设置工厂类型
        objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
    }

    private static <T> boolean withExtensionAnnotation(Class<T> type) {
        return type.isAnnotationPresent(SPI.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        // 必须是接口
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
        }
        // 必须包含 @SPI 注解
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type (" + type +
                    ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
        }
        // 以接口的类对象为 key， 从缓存中获取数据
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            // 保存接口类对象到对应的 ExtensionLoader 的映射
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    // For testing purposes only
    public static void resetExtensionLoader(Class type) {
        ExtensionLoader loader = EXTENSION_LOADERS.get(type);
        if (loader != null) {
            // Remove all instances associated with this loader as well
            Map<String, Class<?>> classes = loader.getExtensionClasses();
            for (Map.Entry<String, Class<?>> entry : classes.entrySet()) {
                EXTENSION_INSTANCES.remove(entry.getValue());
            }
            classes.clear();
            EXTENSION_LOADERS.remove(type);
        }
    }

    private static ClassLoader findClassLoader() {
        return ClassUtils.getClassLoader(ExtensionLoader.class);
    }

    public String getExtensionName(T extensionInstance) {
        return getExtensionName(extensionInstance.getClass());
    }

    public String getExtensionName(Class<?> extensionClass) {
        getExtensionClasses();// load class
        return cachedNames.get(extensionClass);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, key, null)}
     * 获得符合自动激活条件的拓展对象数组
     * @param url url
     * @param key url parameter key which used to get extension point names
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String, String)
     */
    public List<T> getActivateExtension(URL url, String key) {
        return getActivateExtension(url, key, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, values, null)}
     *
     * @param url    url
     * @param values extension point names
     * @return extension list which are activated
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String[] values) {
        return getActivateExtension(url, values, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, url.getParameter(key).split(","), null)}
     *
     * @param url   url
     * @param key   url parameter key which used to get extension point names
     * @param group group
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String key, String group) {
        String value = url.getParameter(key);
        return getActivateExtension(url, StringUtils.isEmpty(value) ? null : COMMA_SPLIT_PATTERN.split(value), group);
    }

    /**
     * Get activate extensions.
     *
     * @param url    url
     * @param values extension point names
     * @param group  group
     * @return extension list which are activated
     * @see org.apache.dubbo.common.extension.Activate
     */
    public List<T> getActivateExtension(URL url, String[] values, String group) {
        List<T> exts = new ArrayList<>();
        List<String> names = values == null ? new ArrayList<>(0) : Arrays.asList(values);
        // names 中不包含 -default，即没有移除所有默认过滤器。例如，<dubbo:service filter="-default" />，代表移除所有默认过滤器
        if (!names.contains(REMOVE_VALUE_PREFIX + DEFAULT_KEY)) {
            // 加载配置文件中的配置信息，加载过程中，会缓存配置名称 -> @Activate 的映射到 cachedActivates
            getExtensionClasses();
            // 遍历 cachedActivates
            for (Map.Entry<String, Object> entry : cachedActivates.entrySet()) {
                // 例如，META-INF/dubbo/internal/org.apache.dubbo.rpc.Filter 文件中的 monitor
                String name = entry.getKey();
                // MonitorFilter 上的 @Activate 注解
                Object activate = entry.getValue();

                String[] activateGroup, activateValue;

                if (activate instanceof Activate) {
                    // 以 MonitorFilter 上的 @Activate 为例，其 group 值为 {PROVIDER, CONSUMER}，activateGroup 为 [provider, consumer]
                    activateGroup = ((Activate) activate).group();
                    activateValue = ((Activate) activate).value();
                } else if (activate instanceof com.alibaba.dubbo.common.extension.Activate) {
                    activateGroup = ((com.alibaba.dubbo.common.extension.Activate) activate).group();
                    activateValue = ((com.alibaba.dubbo.common.extension.Activate) activate).value();
                } else {
                    continue;
                }
                if (isMatchGroup(group, activateGroup)
                        && !names.contains(name)
                        && !names.contains(REMOVE_VALUE_PREFIX + name)
                        && isActive(activateValue, url)) {
                    // 根据 name 获得拓展实例
                    exts.add(getExtension(name));
                }
            }
            exts.sort(ActivateComparator.COMPARATOR);
        }
        List<T> usrs = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            // name 不以 - 开头且整个 names 中不包含 -name
            if (!name.startsWith(REMOVE_VALUE_PREFIX)
                    && !names.contains(REMOVE_VALUE_PREFIX + name)) {
                // 如果 name == default
                if (DEFAULT_KEY.equals(name)) {
                    // usrs 不为空，把 usrs 插入 exts 的开头
                    if (!usrs.isEmpty()) {
                        exts.addAll(0, usrs);
                        usrs.clear();
                    }
                } else {
                    //根据 name 获取拓展实例，并插入 usrs 中
                    usrs.add(getExtension(name));
                }
            }
        }
        // usrs 不为空，把 usrs 插入到 exts 的后面
        if (!usrs.isEmpty()) {
            exts.addAll(usrs);
        }
        return exts;
    }

    private boolean isMatchGroup(String group, String[] groups) {
        if (StringUtils.isEmpty(group)) {
            return true;
        }
        if (groups != null && groups.length > 0) {
            for (String g : groups) {
                if (group.equals(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isActive(String[] keys, URL url) {
        if (keys.length == 0) {
            return true;
        }
        for (String key : keys) {
            for (Map.Entry<String, String> entry : url.getParameters().entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if ((k.equals(key) || k.endsWith("." + key))
                        && ConfigUtils.isNotEmpty(v)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get extension's instance. Return <code>null</code> if extension is not found or is not initialized. Pls. note
     * that this method will not trigger extension load.
     * <p>
     * In order to trigger extension load, call {@link #getExtension(String)} instead.
     *
     * @see #getExtension(String)
     */
    @SuppressWarnings("unchecked")
    public T getLoadedExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Holder<Object> holder = getOrCreateHolder(name);
        return (T) holder.get();
    }

    private Holder<Object> getOrCreateHolder(String name) {
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }
        return holder;
    }

    /**
     * Return the list of extensions which are already loaded.
     * <p>
     * Usually {@link #getSupportedExtensions()} should be called in order to get all extensions.
     *
     * @see #getSupportedExtensions()
     */
    public Set<String> getLoadedExtensions() {
        return Collections.unmodifiableSet(new TreeSet<>(cachedInstances.keySet()));
    }

    public Object getLoadedAdaptiveExtensionInstances() {
        return cachedAdaptiveInstance.get();
    }

    /**
     * Find the extension with the given name. If the specified name is not found, then {@link IllegalStateException}
     * will be thrown.
     *
     * 返回指定名字的扩展对象，如果指定名字的扩展不存在，则抛出异常
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        if ("true".equals(name)) {
            return getDefaultExtension();
        }
        // 从缓存中获取或创建一个新的 Holder 实例，用于持有目标对象
        final Holder<Object> holder = getOrCreateHolder(name);
        Object instance = holder.get();
        // 双重检查
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    // 创建扩展实例
                    instance = createExtension(name);
                    // 缓存实例到 holder 中
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * Return default extension, return <code>null</code> if it's not configured.
     */
    public T getDefaultExtension() {
        getExtensionClasses();
        if (StringUtils.isBlank(cachedDefaultName) || "true".equals(cachedDefaultName)) {
            return null;
        }
        return getExtension(cachedDefaultName);
    }

    public boolean hasExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Class<?> c = this.getExtensionClass(name);
        return c != null;
    }

    public Set<String> getSupportedExtensions() {
        Map<String, Class<?>> clazzes = getExtensionClasses();
        return Collections.unmodifiableSet(new TreeSet<>(clazzes.keySet()));
    }

    /**
     * Return default extension name, return <code>null</code> if not configured.
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return cachedDefaultName;
    }

    /**
     * Register new extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension with the same name has already been registered.
     */
    public void addExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement the Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " already exists (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
        } else {
            if (cachedAdaptiveClass != null) {
                throw new IllegalStateException("Adaptive Extension already exists (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
        }
    }

    /**
     * Replace the existing extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension to be placed doesn't exist
     * @deprecated not recommended any longer, and use only when test
     */
    @Deprecated
    public void replaceExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (!cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " doesn't exist (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
            cachedInstances.remove(name);
        } else {
            if (cachedAdaptiveClass == null) {
                throw new IllegalStateException("Adaptive Extension doesn't exist (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
            cachedAdaptiveInstance.set(null);
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * 获得自适应拓展对象
     */
    public T getAdaptiveExtension() {
        // 从缓存 cachedAdaptiveInstance 中获取自适应拓展
        Object instance = cachedAdaptiveInstance.get();
        // 缓存未命中
        if (instance == null) {
            // 创建自适应拓展类时没有报错
            if (createAdaptiveInstanceError == null) {
                // 双重校验
                synchronized (cachedAdaptiveInstance) {
                    instance = cachedAdaptiveInstance.get();
                    if (instance == null) {
                        try {
                            // 创建自适应拓展
                            instance = createAdaptiveExtension();
                            // 设置自适应拓展到缓存中
                            cachedAdaptiveInstance.set(instance);
                        } catch (Throwable t) {
                            // 记录异常
                            createAdaptiveInstanceError = t;
                            throw new IllegalStateException("Failed to create adaptive instance: " + t.toString(), t);
                        }
                    }
                }
            } else {
                // 若之前创建时报错，则抛出异常
                throw new IllegalStateException("Failed to create adaptive instance: " + createAdaptiveInstanceError.toString(), createAdaptiveInstanceError);
            }
        }

        return (T) instance;
    }

    private IllegalStateException findException(String name) {
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (entry.getKey().toLowerCase().contains(name.toLowerCase())) {
                return entry.getValue();
            }
        }
        StringBuilder buf = new StringBuilder("No such extension " + type.getName() + " by name " + name);


        int i = 1;
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (i == 1) {
                buf.append(", possible causes: ");
            }

            buf.append("\r\n(");
            buf.append(i++);
            buf.append(") ");
            buf.append(entry.getKey());
            buf.append(":\r\n");
            buf.append(StringUtils.toString(entry.getValue()));
        }
        return new IllegalStateException(buf.toString());
    }

    @SuppressWarnings("unchecked")
    /**
     * 创建拓展名的拓展对象
     *
     * @param name 拓展名
     * @return 拓展对象
     */
    private T createExtension(String name) {
        // 从配置文件中加载所有的拓展类，获取 配置项名称 到 配置类 的映射关系
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw findException(name);
        }
        try {
            // 以 clazz 类对象为 key， 从缓存中获取对应的实例
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                // 缓存中不存在该类对象的实例，则在此构建其实例，并存入缓存
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            // dubbo ioc
            // 注入 instance 依赖的属性
            injectExtension(instance);
            // 如果包装对象集合不为空，把 instance 封装成包装对象实例并返回
            Set<Class<?>> wrapperClasses = cachedWrapperClasses;
            if (CollectionUtils.isNotEmpty(wrapperClasses)) {
                for (Class<?> wrapperClass : wrapperClasses) {
                    instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
                }
            }
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance (name: " + name + ", class: " +
                    type + ") couldn't be instantiated: " + t.getMessage(), t);
        }
    }

    /**
     * 为 instance 注入依赖的属性
     * @param instance
     * @return
     */
    private T injectExtension(T instance) {
        try {
            if (objectFactory != null) {
                // 遍历目标类的所有方法
                for (Method method : instance.getClass().getMethods()) {
                    // 判断当前方法是否是 public 修饰符，以 set 开头，且只有一个参数
                    if (isSetter(method)) {
                        /**
                         * Check {@link DisableInject} to see if we need auto injection for this property
                         */
                        // 方法上加了 DisableInject 注解，说明不需要 dubbo 注入这个属性
                        if (method.getAnnotation(DisableInject.class) != null) {
                            continue;
                        }
                        // 获取当前方法的参数类型
                        Class<?> pt = method.getParameterTypes()[0];
                        // 如果参数类型是一些原始类型或基本类型，则不进行注入
                        // java.lang.Boolean, java.lang.Byte, java.lang.Short, java.lang.Integer, java.lang.Long, java.lang.Float, java.lang.Double, java.lang.Void
                        // String.class, Boolean.class, Character.class, Number.class 和它的子类， Date.class 和它的子类
                        if (ReflectUtils.isPrimitives(pt)) {
                            continue;
                        }
                        try {
                            // 获得属性名，比如 setName 方法对应属性名 name
                            String property = getSetterProperty(method);
                            // 从 objectFactory 中获取依赖对象
                            Object object = objectFactory.getExtension(pt, property);
                            if (object != null) {
                                // 通过反射调用 setter 方法设置依赖
                                method.invoke(instance, object);
                            }
                        } catch (Exception e) {
                            logger.error("Failed to inject via method " + method.getName()
                                    + " of interface " + type.getName() + ": " + e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance;
    }

    /**
     * get properties name for setter, for instance: setVersion, return "version"
     * <p>
     * return "", if setter name with length less than 3
     */
    private String getSetterProperty(Method method) {
        return method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
    }

    /**
     * return true if and only if:
     * <p>
     * 1, public
     * <p>
     * 2, name starts with "set"
     * <p>
     * 3, only has one parameter
     */
    private boolean isSetter(Method method) {
        return method.getName().startsWith("set")
                && method.getParameterTypes().length == 1
                && Modifier.isPublic(method.getModifiers());
    }

    private Class<?> getExtensionClass(String name) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (name == null) {
            throw new IllegalArgumentException("Extension name == null");
        }
        return getExtensionClasses().get(name);
    }

    private Map<String, Class<?>> getExtensionClasses() {
        // 从缓存中获取映射关系
        Map<String, Class<?>> classes = cachedClasses.get();
        // 双重检查
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    // 从配置文件加载拓展类，并创建配置项名称到配置类的映射 map
                    classes = loadExtensionClasses();
                    // 把映射 map 存到缓存中
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }



    /**
     * 加载拓展实现类数组
     *
     * 无需声明 synchronized，因为唯一调用本方法的位置就在 synchronized 块中
     * synchronized in getExtensionClasses
     *
     * @return 拓展名->拓展类对象的映射
     */
    private Map<String, Class<?>> loadExtensionClasses() {
        // 提取接口 spi 注解的 value 作为默认拓展名并缓存
        cacheDefaultExtensionName();

        Map<String, Class<?>> extensionClasses = new HashMap<>();
        // 扫描指定路径下以 type 全名命名的文件
        loadDirectory(extensionClasses, DUBBO_INTERNAL_DIRECTORY, type.getName());
        loadDirectory(extensionClasses, DUBBO_INTERNAL_DIRECTORY, type.getName().replace("org.apache", "com.alibaba"));
        loadDirectory(extensionClasses, DUBBO_DIRECTORY, type.getName());
        loadDirectory(extensionClasses, DUBBO_DIRECTORY, type.getName().replace("org.apache", "com.alibaba"));
        loadDirectory(extensionClasses, SERVICES_DIRECTORY, type.getName());
        loadDirectory(extensionClasses, SERVICES_DIRECTORY, type.getName().replace("org.apache", "com.alibaba"));
        return extensionClasses;
    }

    /**
     * extract and cache default extension name if exists
     */
    private void cacheDefaultExtensionName() {
        // 获取接口上的 spi 注解，type 在调用 getExtensionLoader 时被赋值
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation != null) {
            String value = defaultAnnotation.value();
            // 注解上的 value 值不为空
            if ((value = value.trim()).length() > 0) {
                // 按 ， 分割，如果有多个值，则抛出异常
                String[] names = NAME_SEPARATOR.split(value);
                if (names.length > 1) {
                    throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                            + ": " + Arrays.toString(names));
                }
                // 否则缓存 value 的值作为默认拓展名
                if (names.length == 1) {
                    cachedDefaultName = names[0];
                }
            }
        }
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type) {
        // 文件名 = 文件夹路径 + type 接口的全限定名
        String fileName = dir + type;
        try {
            Enumeration<java.net.URL> urls;
            // 获取用来加载资源的类加载器，先尝试获取当前线程使用的类加载器，没有就尝试获取 ExtensionLoader 的类加载器，再没有就获取系统类加载器 bootstrap classloader
            ClassLoader classLoader = findClassLoader();
            // 根据文件名加载所有的同名文件，获取所有的资源链接
            if (classLoader != null) {
                urls = classLoader.getResources(fileName);
            } else {
                urls = ClassLoader.getSystemResources(fileName);
            }
            if (urls != null) {
                // 迭代资源链接，加载文件中的每个资源配置
                while (urls.hasMoreElements()) {
                    java.net.URL resourceURL = urls.nextElement();
                    // 解析配置文件，构造、缓存对象以及对象相关的映射关系
                    loadResource(extensionClasses, classLoader, resourceURL);
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", description file: " + fileName + ").", t);
        }
    }

    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, java.net.URL resourceURL) {
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
                String line;
                // 按行读取配置内容
                while ((line = reader.readLine()) != null) {
                    // 定位 # 字符
                    final int ci = line.indexOf('#');
                    if (ci >= 0) {
                        // 截取 # 之前的字符串，因为 # 之后的内容为注释，需要忽略
                        line = line.substring(0, ci);
                    }
                    line = line.trim();
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            // 定位 = 的位置
                            int i = line.indexOf('=');
                            if (i > 0) {
                                // 以 = 为界，截取键与值
                                // key
                                name = line.substring(0, i).trim();
                                // 类限定名
                                line = line.substring(i + 1).trim();
                            }
                            if (line.length() > 0) {
                                // 根据类限定名和指定的类加载器，通过反射加载类，构造该类的 class 类对象，并进行缓存，最终保存 name 到 class 类对象的映射关系到 extensionClasses
                                loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name);
                            }
                        } catch (Throwable t) {
                            IllegalStateException e = new IllegalStateException("Failed to load extension class (interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }

    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name) throws NoSuchMethodException {
        // 判断拓展实现类是否实现了拓展接口，否：抛出异常
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Error occurred when loading extension class (interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    + clazz.getName() + " is not subtype of interface.");
        }
        // 检测目标类上是否有 Adaptive 注解
        if (clazz.isAnnotationPresent(Adaptive.class)) {
            // 有的话，把目标类缓存到 cachedAdaptiveClass 属性中，如果有 Adaptive 注解的类超过一个，则抛出异常
            cacheAdaptiveClass(clazz);
        } else if (isWrapperClass(clazz)) {
            // 检测当前类是否是包装类：目标类是否拥有一个构造方法，入参只有一个并且类型是自己实现的接口类型(AOP?)
            // 是的话，把目标类缓存到 cachedWrapperClasses 中
            cacheWrapperClass(clazz);
        } else {
            // 程序进入此分支，表明目标类是一个普通的拓展类
            // 检测目标类是否有默认的构造方法，没有则抛出异常
            clazz.getConstructor();
            if (StringUtils.isEmpty(name)) {
                // 如果 name（配置文件中的 key）为空，则尝试获取目标类上 Extension 注解的 value 值作为 key，或者使用小写的类名作为 key
                name = findAnnotationName(clazz);
                if (name.length() == 0) {
                    throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
                }
            }
            // 切分 name
            String[] names = NAME_SEPARATOR.split(name);
            if (ArrayUtils.isNotEmpty(names)) {
                // 如果目标类上有 Activate 注解，则使用 names 数组的第一个元素作为 key
                // 在 cachedActivates 中存储 name 到 Activate 的映射
                cacheActivateClass(clazz, names[0]);
                for (String n : names) {
                    // 存储目标类 clazz 到 name 的映射
                    cacheName(clazz, n);
                    // 在拓展类 map 中保存 name 到目标类 clazz 的映射
                    saveInExtensionClass(extensionClasses, clazz, n);
                }
            }
        }
    }

    /**
     * cache name
     */
    private void cacheName(Class<?> clazz, String name) {
        if (!cachedNames.containsKey(clazz)) {
            cachedNames.put(clazz, name);
        }
    }

    /**
     * put clazz in extensionClasses
     */
    private void saveInExtensionClass(Map<String, Class<?>> extensionClasses, Class<?> clazz, String name) {
        Class<?> c = extensionClasses.get(name);
        if (c == null) {
            extensionClasses.put(name, clazz);
        } else if (c != clazz) {
            throw new IllegalStateException("Duplicate extension " + type.getName() + " name " + name + " on " + c.getName() + " and " + clazz.getName());
        }
    }

    /**
     * cache Activate class which is annotated with <code>Activate</code>
     * <p>
     * for compatibility, also cache class with old alibaba Activate annotation
     */
    private void cacheActivateClass(Class<?> clazz, String name) {
        Activate activate = clazz.getAnnotation(Activate.class);
        if (activate != null) {
            cachedActivates.put(name, activate);
        } else {
            // support com.alibaba.dubbo.common.extension.Activate
            com.alibaba.dubbo.common.extension.Activate oldActivate = clazz.getAnnotation(com.alibaba.dubbo.common.extension.Activate.class);
            if (oldActivate != null) {
                cachedActivates.put(name, oldActivate);
            }
        }
    }

    /**
     * cache Adaptive class which is annotated with <code>Adaptive</code>
     */
    private void cacheAdaptiveClass(Class<?> clazz) {
        if (cachedAdaptiveClass == null) {
            cachedAdaptiveClass = clazz;
        } else if (!cachedAdaptiveClass.equals(clazz)) {
            throw new IllegalStateException("More than 1 adaptive class found: "
                    + cachedAdaptiveClass.getClass().getName()
                    + ", " + clazz.getClass().getName());
        }
    }

    /**
     * cache wrapper class
     * <p>
     * like: ProtocolFilterWrapper, ProtocolListenerWrapper
     */
    private void cacheWrapperClass(Class<?> clazz) {
        if (cachedWrapperClasses == null) {
            cachedWrapperClasses = new ConcurrentHashSet<>();
        }
        cachedWrapperClasses.add(clazz);
    }

    /**
     * test if clazz is a wrapper class
     * <p>
     * which has Constructor with given class type as its only argument
     */
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> clazz) {
        org.apache.dubbo.common.Extension extension = clazz.getAnnotation(org.apache.dubbo.common.Extension.class);
        if (extension == null) {
            // 例，name = DubboProtocol, type.getSimpleName() = Protocol,
            // 最终返回 dubbo 作为 name
            String name = clazz.getSimpleName();
            if (name.endsWith(type.getSimpleName())) {
                name = name.substring(0, name.length() - type.getSimpleName().length());
            }
            return name.toLowerCase();
        }
        return extension.value();
    }

    @SuppressWarnings("unchecked")
    /**
     * @return 拓展对象实例
     */
    private T createAdaptiveExtension() {
        try {
            // 1. 调用 getAdaptiveExtensionClass 获取自适应拓展类对象
            // 2. 调用 newInstance 通过反射进行实例化
            // 3. 调用 injectExtension 向拓展实例中注入依赖
            return injectExtension((T) getAdaptiveExtensionClass().newInstance());
        } catch (Exception e) {
            throw new IllegalStateException("Can't create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     *
     * @return 自适应拓展类对象
     */
    private Class<?> getAdaptiveExtensionClass() {
        // 通过 spi 获取所有的拓展类，如果当前接口的某个实现类被 Adaptive 注解修饰了，那么该类就会被赋值给 cachedAdaptiveClass 对象
        getExtensionClasses();
        // 检查缓存，缓存不为空，直接返回缓存
        if (cachedAdaptiveClass != null) {
            return cachedAdaptiveClass;
        }
        // 自动生成自适应拓展类的代码实现，并在编译通过后保存到缓存中
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }

    /**
     * 自动生成自适应拓展的代码实现，并编译后返回该类
     *
     * @return
     */
    private Class<?> createAdaptiveExtensionClass() {
        // 构建自适应拓展代码
        String code = new AdaptiveClassCodeGenerator(type, cachedDefaultName).generate();
        // 获取类加载器
        ClassLoader classLoader = findClassLoader();
        // 获取编译器实现类
        org.apache.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        // 编译代码，生成 Class 并返回
        return compiler.compile(code, classLoader);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }

}
