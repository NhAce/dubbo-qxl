package qxl.service.impl;

import qxl.service.Robot;

/**
 * @Author: qxl
 * @Date: 2019/8/14 10:06
 * @Desc:
 */
public class Bumblebee implements Robot {

    private Robot robot;

    @Override
    public void sayHello() {
        System.out.println("Hello, I am Bumblebee.");
    }

    public void setRobot(Robot robot){
        this.robot = robot;
    }
}
