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
package org.apache.dubbo.rpc.model;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProviderModel which is about published services
 */
public class ProviderModel {
    private final String serviceName;
    private final Object serviceInstance;
    private final Class<?> serviceInterfaceClass;
    private final Map<String, List<ProviderMethodModel>> methods = new HashMap<String, List<ProviderMethodModel>>();

    public ProviderModel(String serviceName, Object serviceInstance, Class<?> serviceInterfaceClass) {
        if (null == serviceInstance) {
            throw new IllegalArgumentException("Service[" + serviceName + "]Target is NULL.");
        }
        //接口名称
        this.serviceName = serviceName;
        //接口实现类
        this.serviceInstance = serviceInstance;
        //接口类信息
        this.serviceInterfaceClass = serviceInterfaceClass;
        //保存需要暴露的方法信息，存在 methods 属性中
        initMethod();
    }


    public String getServiceName() {
        return serviceName;
    }

    public Class<?> getServiceInterfaceClass() {
        return serviceInterfaceClass;
    }

    public Object getServiceInstance() {
        return serviceInstance;
    }

    public List<ProviderMethodModel> getAllMethods() {
        List<ProviderMethodModel> result = new ArrayList<ProviderMethodModel>();
        for (List<ProviderMethodModel> models : methods.values()) {
            result.addAll(models);
        }
        return result;
    }

    public ProviderMethodModel getMethodModel(String methodName, String[] argTypes) {
        List<ProviderMethodModel> methodModels = methods.get(methodName);
        if (methodModels != null) {
            for (ProviderMethodModel methodModel : methodModels) {
                if (Arrays.equals(argTypes, methodModel.getMethodArgTypes())) {
                    return methodModel;
                }
            }
        }
        return null;
    }

    private void initMethod() {
        Method[] methodsToExport = null;
        //获取接口所有方法
        methodsToExport = this.serviceInterfaceClass.getMethods();

        //遍历方法列表
        for (Method method : methodsToExport) {
            //方法访问属性设为可访问
            method.setAccessible(true);
            //为每个方法创建一个长度为 1 的 List<ProviderMethodModel> 对象 methodModels
            //根据方法和接口名创建 providerMethodModel 对象，装入 methodModels 列表中
            //在 methods 属性中增加键值对属性，key 为方法名，value 为 methodModels
            List<ProviderMethodModel> methodModels = methods.get(method.getName());
            if (methodModels == null) {
                methodModels = new ArrayList<ProviderMethodModel>(1);
                methods.put(method.getName(), methodModels);
            }
            methodModels.add(new ProviderMethodModel(method, serviceName));
        }
    }

}
