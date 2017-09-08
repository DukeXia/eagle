package eagle.jfaster.org.benchmark.impl;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.concurrent.CountDownLatch;

/**
 * Created by fangyanpeng on 2017/9/8.
 */
public class EagleBenchmarkServer {

    public static void main(String[] args) throws InterruptedException {
        ApplicationContext appCtx = new ClassPathXmlApplicationContext("classpath*:benchmark-server.xml");
        System.out.println("-----server running-----");
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }
}
