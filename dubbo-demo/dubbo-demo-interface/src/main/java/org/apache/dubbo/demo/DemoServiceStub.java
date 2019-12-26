package org.apache.dubbo.demo;

/**
 * @Author: qxl
 * @Date: 2019/9/12 13:54
 * @Desc:
 */
public class DemoServiceStub implements DemoService {
    private final DemoService demoService;

    public DemoServiceStub(DemoService demoService){
        this.demoService = demoService;
    }

    @Override
    public String sayHello(String name) {
        if (name == null || "".equals(name)){
            System.out.println("name not valid...");
            return "remote call failed..";
        }
        return demoService.sayHello(name);
    }
}
