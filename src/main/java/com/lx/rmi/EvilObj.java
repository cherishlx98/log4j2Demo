package com.lx.rmi;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.spi.ObjectFactory;
import java.util.Hashtable;

/**
 * 在这里实现了ObjectFactory接口，主要是针对EvilObj类无法转换为ObjectFactory对象，其他Java版本中可能不存在这个问题
 * @Author: Xin Liu
 * @Date: 2022/2/15
 */
public class EvilObj implements ObjectFactory {
    static{
        System.out.println("在哪执行的");
    }

    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        return null;
    }
}