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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;

/**
 * Code generator for Adaptive class
 */
public class AdaptiveClassCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveClassCodeGenerator.class);

    private static final String CLASSNAME_INVOCATION = "org.apache.dubbo.rpc.Invocation";

    private static final String CODE_PACKAGE = "package %s;\n";

    private static final String CODE_IMPORTS = "import %s;\n";

    private static final String CODE_CLASS_DECLARATION = "public class %s$Adaptive implements %s {\n";

    private static final String CODE_METHOD_DECLARATION = "public %s %s(%s) %s {\n%s}\n";

    private static final String CODE_METHOD_ARGUMENT = "%s arg%d";

    private static final String CODE_METHOD_THROWS = "throws %s";

    private static final String CODE_UNSUPPORTED = "throw new UnsupportedOperationException(\"The method %s of interface %s is not adaptive method!\");\n";

    private static final String CODE_URL_NULL_CHECK = "if (arg%d == null) throw new IllegalArgumentException(\"url == null\");\n%s url = arg%d;\n";

    private static final String CODE_EXT_NAME_ASSIGNMENT = "String extName = %s;\n";

    private static final String CODE_EXT_NAME_NULL_CHECK = "if(extName == null) "
                    + "throw new IllegalStateException(\"Failed to get extension (%s) name from url (\" + url.toString() + \") use keys(%s)\");\n";

    private static final String CODE_INVOCATION_ARGUMENT_NULL_CHECK = "if (arg%d == null) throw new IllegalArgumentException(\"invocation == null\"); "
                    + "String methodName = arg%d.getMethodName();\n";


    private static final String CODE_EXTENSION_ASSIGNMENT = "%s extension = (%<s)%s.getExtensionLoader(%s.class).getExtension(extName);\n";

    private static final String CODE_EXTENSION_METHOD_INVOKE_ARGUMENT = "arg%d";

    private final Class<?> type;

    private String defaultExtName;

    public AdaptiveClassCodeGenerator(Class<?> type, String defaultExtName) {
        this.type = type;
        this.defaultExtName = defaultExtName;
    }

    /**
     * test if given type has at least one method annotated with <code>SPI</code>
     */
    private boolean hasAdaptiveMethod() {
        return Arrays.stream(type.getMethods()).anyMatch(m -> m.isAnnotationPresent(Adaptive.class));
    }

    /**
     * generate and return class code
     */
    public String generate() {
        // no need to generate adaptive class since there's no adaptive method found.
        // 遍历接口中的每个方法，判断每个方法上是否有 Adaptive 注解
        // 如果所有的方法上均无 Adaptive 注解，则抛出异常
        if (!hasAdaptiveMethod()) {
            throw new IllegalStateException("No adaptive method exist on extension " + type.getName() + ", refuse to create the adaptive class!");
        }
        // 准备构建拓展类代码
        StringBuilder code = new StringBuilder();
        // 生成 package 代码：package + type 所在包
        code.append(generatePackageInfo());
        // 生成 import 代码：import + ExtensionLoader 全限定名
        code.append(generateImports());
        // 生成类代码：public class + type 简单名称 + $Adaptive + implements + type 全限定名 + {
        code.append(generateClassDeclaration());
        // 生成方法
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            code.append(generateMethod(method));
        }
        // 类以 } 结尾
        code.append("}");

        if (logger.isDebugEnabled()) {
            logger.debug(code.toString());
        }
        return code.toString();
    }

    /**
     * generate package info
     */
    private String generatePackageInfo() {
        return String.format(CODE_PACKAGE, type.getPackage().getName());
    }

    /**
     * generate imports
     */
    private String generateImports() {
        return String.format(CODE_IMPORTS, ExtensionLoader.class.getName());
    }

    /**
     * generate class declaration
     */
    private String generateClassDeclaration() {
        return String.format(CODE_CLASS_DECLARATION, type.getSimpleName(), type.getCanonicalName());
    }

    /**
     * generate method not annotated with Adaptive with throwing unsupported exception
     */
    private String generateUnsupported(Method method) {
        return String.format(CODE_UNSUPPORTED, method, type.getName());
    }

    /**
     * get index of parameter with type URL
     */
    private int getUrlTypeIndex(Method method) {
        int urlTypeIndex = -1;
        Class<?>[] pts = method.getParameterTypes();
        for (int i = 0; i < pts.length; ++i) {
            if (pts[i].equals(URL.class)) {
                urlTypeIndex = i;
                break;
            }
        }
        return urlTypeIndex;
    }

    /**
     * generate method declaration
     */
    private String generateMethod(Method method) {
        // 获取方法的返回值类型的全限定名
        String methodReturnType = method.getReturnType().getCanonicalName();
        // 获取方法名
        String methodName = method.getName();
        // 构建方法体内容
        String methodContent = generateMethodContent(method);
        // 构造形参列表，格式为： 参数类型0 arg0，参数类型1 arg1， 参数类型2 arg2 ...
        String methodArgs = generateMethodArguments(method);
        // 构造方法的 throw 语句，格式为：throws Exception1, Exception2,...
        String methodThrows = generateMethodThrows(method);
        // 组装整个方法
        // public + 返回值类型的全限定名 + 方法名(形参列表) + throw 语句 + { \n 方法体内容} \n
        return String.format(CODE_METHOD_DECLARATION, methodReturnType, methodName, methodArgs, methodThrows, methodContent);
    }

    /**
     * generate method arguments
     */
    private String generateMethodArguments(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return IntStream.range(0, pts.length)
                        .mapToObj(i -> String.format(CODE_METHOD_ARGUMENT, pts[i].getCanonicalName(), i))
                        .collect(Collectors.joining(", "));
    }

    /**
     * generate method throws
     */
    private String generateMethodThrows(Method method) {
        Class<?>[] ets = method.getExceptionTypes();
        if (ets.length > 0) {
            String list = Arrays.stream(ets).map(Class::getCanonicalName).collect(Collectors.joining(", "));
            return String.format(CODE_METHOD_THROWS, list);
        } else {
            return "";
        }
    }

    /**
     * generate method URL argument null check
     */
    private String generateUrlNullCheck(int index) {
        return String.format(CODE_URL_NULL_CHECK, index, URL.class.getName(), index);
    }

    /**
     * generate method content
     */
    private String generateMethodContent(Method method) {
        Adaptive adaptiveAnnotation = method.getAnnotation(Adaptive.class);
        StringBuilder code = new StringBuilder(512);
        // 如果方法上无 Adaptive 注解，则生成 throw new UnsupportedOperationException("The method %s of interface %s is not adaptive method! 代码，并返回
        if (adaptiveAnnotation == null) {
            return generateUnsupported(method);
        } else {
            // 在方法参数列表中，获取 URL 类型参数的位置
            int urlTypeIndex = getUrlTypeIndex(method);

            // found parameter in URL type
            // 位置不是 -1，表示当前方法的参数列表中存在 URL 类型的参数
            if (urlTypeIndex != -1) {
                // Null Point check
                // 为 URL 类型参数生成判空代码，格式如下：
                // if (arg + urlTypeIndex == null) throw new IllegalArgumentException("url == null");
                // 并把参数赋值给新建的 url 变量，格式如下
                //  org.apache.dubbo.common.URL url = arg + urlTypeIndex;
                code.append(generateUrlNullCheck(urlTypeIndex));
            } else {
                // did not find parameter in URL type
                // 参数列表中不存在 URL 类型的参数，则尝试从参数列表中的参数中获取能返回 URL 的方法
                code.append(generateUrlAssignmentIndirectly(method));
            }
            // 获取 Adaptive 注解上的 value 的值，如果没有显式配置
            // 则默认使用接口名，对驼峰命名的接口名进行小写转换并以 . 分割，规则如下
            // Robot --> robot，RobotService --> robot.service，URL --> u.r.l
            String[] value = getMethodAdaptiveValue(adaptiveAnnotation);

            // 检测当前方法的参数列表中是否有 org.apache.dubbo.rpc.Invocation 参数
            boolean hasInvocation = hasInvocationArgument(method);
            // 为 invocation 类型生成判空代码，格式如下：
            // if ((arg + i) == null) throw new IllegalArgumentException("invocation == null");
            // 生成 getMethodName 方法调用代码，格式为：
            // String methodName = (arg + i).getMethodName();
            code.append(generateInvocationArgumentNullCheck(method));
            // 生成从 URL 中获取拓展名的代码，赋值给 extName
            code.append(generateExtNameAssignment(value, hasInvocation));
            // check extName == null?
            // 生成判断 extName 是否为 null 的代码，格式为：
            // if(extName == null) throw new IllegalStateException("Failed to get extension (接口名) name from url (" + url.toString() + ") use keys(方法上 Adaptive 注解的值)");
            code.append(generateExtNameNullCheck(value));
            // 生成拓展获取代码，格式如下：
            // %s extension = (%<s)%s.getExtensionLoader(%s.class).getExtension(extName);
            // type全限定名 extension = (type全限定名)ExtensionLoader
            //     .getExtensionLoader(type全限定名.class).getExtension(extName);
            // Tips: 格式化字符串中的 %<s 表示使用前一个转换符所描述的参数，即 type 全限定名
            code.append(generateExtensionAssignment());

            // return statement
            // 生成整个方法的 return 语句0
            code.append(generateReturnAndInvocation(method));
        }

        return code.toString();
    }

    /**
     * generate code for variable extName null check
     */
    private String generateExtNameNullCheck(String[] value) {
        return String.format(CODE_EXT_NAME_NULL_CHECK, type.getName(), Arrays.toString(value));
    }

    /**
     * generate extName assigment code
     */
    private String generateExtNameAssignment(String[] value, boolean hasInvocation) {
        // TODO: refactor it
        String getNameCode = null;
        // 以下循环的目的是生成从 URL 中获取拓展名的代码
        // 倒序遍历 Adaptive 注解上的 value 值
        for (int i = value.length - 1; i >= 0; --i) {
            // 当 i 为最后一个元素坐标时
            if (i == value.length - 1) {
                // 默认拓展名不为空
                if (null != defaultExtName) {
                    // protocol 是 url 的一部分，可通过 getProtocol 方法获取，其他的则是从 URL 参数中获取。
                    // 因为获取方式不同，所以这里要判断 value[i] 是否为 protocol
                    if (!"protocol".equals(value[i])) {
                        // hasInvocation 标识方法参数列表中是否有 Invocation 类型的参数
                        if (hasInvocation) {
                            // 生成的代码功能等价于下面的代码：
                            // url.getMethodParameter(methodName, value[i], defaultExtName)
                            // 以 LoadBalance 接口的 select 方法为例，最终生成的代码如下：
                            // url.getMethodParameter(methodName, "loadbalance", "random")
                            getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                        } else {
                            // 生成的代码功能等价于下面的代码：
                            //   url.getParameter(value[i], defaultExtName)
                            getNameCode = String.format("url.getParameter(\"%s\", \"%s\")", value[i], defaultExtName);
                        }
                    } else {
                        // 生成的代码功能等价于下面的代码：
                        //   ( url.getProtocol() == null ? defaultExtName : url.getProtocol() )
                        getNameCode = String.format("( url.getProtocol() == null ? \"%s\" : url.getProtocol() )", defaultExtName);
                    }
                } else {
                    // 默认拓展名为空
                    if (!"protocol".equals(value[i])) {
                        if (hasInvocation) {
                            // 生成的代码格式同上
                            getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                        } else {
                            // 生成的代码功能等价于下面的代码：
                            //   url.getParameter(value[i])
                            getNameCode = String.format("url.getParameter(\"%s\")", value[i]);
                        }
                    } else {
                        // 生成从 url 中获取协议的代码，比如 "dubbo"
                        getNameCode = "url.getProtocol()";
                    }
                }
            } else {
                // i 不是最后一个元素的坐标时
                if (!"protocol".equals(value[i])) {
                    if (hasInvocation) {
                        // 生成代码格式同上
                        getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                    } else {
                        // 生成的代码功能等价于下面的代码：
                        //   url.getParameter(value[i], getNameCode)
                        // 以 Transporter 接口的 connect 方法为例，最终生成的代码如下：
                        //   url.getParameter("client", url.getParameter("transporter", "netty"))
                        getNameCode = String.format("url.getParameter(\"%s\", %s)", value[i], getNameCode);
                    }
                } else {
                    // 生成的代码功能等价于下面的代码：
                    //   url.getProtocol() == null ? getNameCode : url.getProtocol()
                    // 以 Protocol 接口的 connect 方法为例，最终生成的代码如下：
                    //   url.getProtocol() == null ? "dubbo" : url.getProtocol()
                    getNameCode = String.format("url.getProtocol() == null ? (%s) : url.getProtocol()", getNameCode);
                }
            }
        }
        // 将生成的代码赋值给拓展类中的 extName 属性，格式如下：
        // String extName = getNameCode;
        return String.format(CODE_EXT_NAME_ASSIGNMENT, getNameCode);
    }

    /**
     * @return
     */
    private String generateExtensionAssignment() {
        return String.format(CODE_EXTENSION_ASSIGNMENT, type.getName(), ExtensionLoader.class.getSimpleName(), type.getName());
    }

    /**
     * generate method invocation statement and return it if necessary
     */
    private String generateReturnAndInvocation(Method method) {
        // 判断返回值类型，决定是否需要返回的关键字
        String returnStatement = method.getReturnType().equals(void.class) ? "" : "return ";
        // 构造参数列表，格式为 arg0, arg1, arg2, .... , argn
        String args = IntStream.range(0, method.getParameters().length)
                .mapToObj(i -> String.format(CODE_EXTENSION_METHOD_INVOKE_ARGUMENT, i))
                .collect(Collectors.joining(", "));
        // 组装返回语句，格式为：
        // returnStatement + extension.方法名(参数列表)
        return returnStatement + String.format("extension.%s(%s);\n", method.getName(), args);
    }

    /**
     * test if method has argument of type <code>Invocation</code>
     */
    private boolean hasInvocationArgument(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return Arrays.stream(pts).anyMatch(p -> CLASSNAME_INVOCATION.equals(p.getName()));
    }

    /**
     * generate code to test argument of type <code>Invocation</code> is null
     */
    private String generateInvocationArgumentNullCheck(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return IntStream.range(0, pts.length).filter(i -> CLASSNAME_INVOCATION.equals(pts[i].getName()))
                        .mapToObj(i -> String.format(CODE_INVOCATION_ARGUMENT_NULL_CHECK, i, i))
                        .findFirst().orElse("");
    }

    /**
     * get value of adaptive annotation or if empty return splitted simple name
     */
    private String[] getMethodAdaptiveValue(Adaptive adaptiveAnnotation) {
        // Adaptive 注解值 value 类型是 String[]，可填写多个值，默认是空数组
        String[] value = adaptiveAnnotation.value();
        // value is not set, use the value generated from class name as the key
        // value 没有显示指定，即注解上 value 为空
        if (value.length == 0) {
            // 对驼峰命名的接口名进行小写转换并以 . 分割，规则如下
            // Robot --> robot，RobotService --> robot.service，URL --> u.r.l
            String splitName = StringUtils.camelToSplitName(type.getSimpleName(), ".");
            value = new String[]{splitName};
        }
        return value;
    }

    /**
     * get parameter with type <code>URL</code> from method parameter:
     * <p>
     * test if parameter has method which returns type <code>URL</code>
     * <p>
     * if not found, throws IllegalStateException
     */
    private String generateUrlAssignmentIndirectly(Method method) {
        // 获取方法的所有参数类型
        Class<?>[] pts = method.getParameterTypes();

        // find URL getter method
        // 遍历参数类型
        for (int i = 0; i < pts.length; ++i) {
            // 遍历该类型参数的所有方法
            for (Method m : pts[i].getMethods()) {
                String name = m.getName();
                // 1. 方法名以 get 开头或者方法名的长度大于 3
                // 2. 方法的访问权限为 public
                // 3. 非静态方法
                // 4. 方法参数数量为 0
                // 5. 方法的返回值类型是 URL
                if ((name.startsWith("get") || name.length() > 3)
                        && Modifier.isPublic(m.getModifiers())
                        && !Modifier.isStatic(m.getModifiers())
                        && m.getParameterTypes().length == 0
                        && m.getReturnType() == URL.class) {
                    // 对方法的第 i 个参数 argi 生成判空代码
                    // 对 argi.name() 的结果生成判空代码
                    // 构建新变量 url，并把 argi.name() 的结果赋值给 url
                    // 格式如下
                    // if (arg + i == null) throw new IllegalArgumentException("pts[i].getName() argument == null");
                    // if ((arg + i).{name}() == null) throw new IllegalArgumentException("pts[i].getName() argument {name} == null");
                    // org.apache.dubbo.common.URL url = arg + i.name();
                    return generateGetUrlNullCheck(i, pts[i], name);
                }
            }
        }

        // getter method not found, throw
        throw new IllegalStateException("Failed to create adaptive class for interface " + type.getName()
                        + ": not found url parameter or url attribute in parameters of method " + method.getName());

    }

    /**
     * 1, test if argi is null
     * 2, test if argi.getXX() returns null
     * 3, assign url with argi.getXX()
     */
    private String generateGetUrlNullCheck(int index, Class<?> type, String method) {
        // Null point check
        StringBuilder code = new StringBuilder();
        code.append(String.format("if (arg%d == null) throw new IllegalArgumentException(\"%s argument == null\");\n",
                index, type.getName()));
        code.append(String.format("if (arg%d.%s() == null) throw new IllegalArgumentException(\"%s argument %s() == null\");\n",
                index, method, type.getName(), method));

        code.append(String.format("%s url = arg%d.%s();\n", URL.class.getName(), index, method));
        return code.toString();
    }

}
