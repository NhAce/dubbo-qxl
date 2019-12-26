package qxl;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.registry.RegistryService;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import qxl.leetCode.ListNode;
import qxl.service.Robot;
import qxl.service.impl.Bumblebee;
import qxl.service.impl.OptimusPrime;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class Application {

    public static void main(String[] args) {
//        ExtensionLoader<Robot> extensionLoader = ExtensionLoader.getExtensionLoader(Robot.class);
//        Robot bumblebee = extensionLoader.getExtension("Bum");
//        bumblebee.sayHello();
//        Robot optimusPrime = extensionLoader.getExtension("Opt");
//        optimusPrime.sayHello();
//        System.out.println(StringUtils.camelToSplitName(Robot.class.getSimpleName(), "."));
//        int[] arr = {4, 6, 3, 7, 2, 1, 5};
//        List<String> strings = new ArrayList<>();
//        System.out.println(RegistryService.class.getName());

//        ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
//        ProxyFactory extension = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getExtension("javassist");
//        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
//        org.apache.dubbo.rpc.Protocol extension = ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension("dubbo");
//        System.out.println(extension.getClass().getCanonicalName());
    }

}
