package org.apache.dubbo.demo;

/**
 * @Author: qxl
 * @Date: 2019/9/12 14:03
 * @Desc:
 */
public class DemoServiceMock implements DemoService {
    @Override
    public String sayHello(String name) {
        System.out.println("DemoServiceMock sayHello....");
        return "remote call failed....";
    }
}
