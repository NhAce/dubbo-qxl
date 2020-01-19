package qxl.service;

import org.apache.dubbo.common.extension.SPI;

/**
 * @Author: qxl
 * @Date: 2019/8/14 10:05
 * @Desc:
 */
@SPI
public interface Robot {
  public void sayHello();
}
