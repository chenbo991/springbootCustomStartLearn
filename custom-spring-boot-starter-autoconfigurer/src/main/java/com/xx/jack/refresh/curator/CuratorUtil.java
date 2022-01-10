package com.xx.jack.refresh.curator;

import com.xx.jack.refresh.scope.RefreshScopeRegistry;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.*;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

/**
 *
 分布式配置中心。特别是要解决@value的实现，动态刷新。

 解决environment的刷新比较简单。（这个environment是spring容器中的一个对象。可以直接修改对象的属性就好了。）
 但是这个主要是解决@value的功能。因为spring的单例bean模式。这个对象在启动的时候，就已经实例化了。对象里面对这个bean也没有getset方法。
 而且我们也不能实现知道这些属性名称是什么。只能通过@value属性知道

 怎么解决呢。因为
 1.可以使用多例。多例在spring容器的getbean的时候，是没有做缓存的。每次都是一次新的初始化bean
 2.也可以扫描所有的带有value的直接。采用反射循环赋值。但是这样几乎没有可行性。
 3.可以使用添加@scope的注解。在spring的getbean的时候，可以采用手动添加在自定义的缓存map中。
 采用一个可以发布订阅的第三方的数据库。

 思路总结，
 1.创建bean的时候，把添加了@Scope("refresh")的注解放在自定义的容器缓存中，
 2.配置中心再修改的时候，订阅节点触发删除容器bean，
 3.bean再吃被使用会重新初始化。这样就实现了这些bean中，@value的值是新的了。

 发布订阅的可以是注册中心。
 */


@Component
public class CuratorUtil implements ApplicationContextAware {

//todo 改为redis的注册发布。去掉zookeeper.


    private static String connnectStr = "192.168.67.139:6379";

    private static CuratorFramework client;

    private static String path = "/config";

//    @Value(("${zookeeper.config.enable:false}"))
//    private boolean enbale;

    @Autowired
    Environment environment;

    private static String zkPropertyName = "zookeeperSource";

    private static String scopeName = "refresh";

    private static ConfigurableApplicationContext applicationContext;

    /**
     * 存放配置文件的属性的map
     */
    private ConcurrentHashMap map = new ConcurrentHashMap();

    private BeanDefinitionRegistry beanDefinitionRegistry;


    /**
     * 代码创建的时候，执行方法。
     */
    @PostConstruct
    public void init() {
//        if (!enbale) {
//            return;
//        }
// todo 改为redis的注册发布。去掉zookeeper.

        RefreshScopeRegistry refreshScopeRegistry = (RefreshScopeRegistry)applicationContext.getBean("refreshScopeRegistry");
        beanDefinitionRegistry = refreshScopeRegistry.getBeanDefinitionRegistry();

        client = CuratorFrameworkFactory.
                builder().
                connectString(connnectStr).
                sessionTimeoutMs(5000).
                retryPolicy(new ExponentialBackoffRetry(1000, 3)).
                build();

        client.start();
        try {
            Stat stat = client.checkExists().forPath(path);
            if (stat == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).
                        forPath(path, "zookeeper config".getBytes());
                TimeUnit.SECONDS.sleep(1);
            } else {
                //1、启动项目的时候，把config下面的子节点加载到spring容器的属性对象中
                addChildToSpringProperty(client, path);
            }

//            nodeCache(client,path);
            //2.添加一个事件监听。对订阅的节点，修改后，可以进行动态刷新
            childNodeCache(client, path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param client
     * @param path
     */
    private void addChildToSpringProperty(CuratorFramework client, String path) {
        if (!checkExistsSpringProperty()) {
            //如果不存在zookeeper的配置属性对象则创建
            createZookeeperSpringProperty();
        }

        //把config目录下的子节点添加到 zk的PropertySource对象中
        MutablePropertySources propertySources = applicationContext.getEnvironment().getPropertySources();
        PropertySource<?> propertySource = propertySources.get(zkPropertyName);
        ConcurrentHashMap zkmap = (ConcurrentHashMap) propertySource.getSource();
        try {
            List<String> strings = client.getChildren().forPath(path);
            for (String string : strings) {
                zkmap.put(string, client.getData().forPath(path + "/" + string));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createZookeeperSpringProperty() {
        MutablePropertySources propertySources = applicationContext.getEnvironment().getPropertySources();
        //把我们的自定义 的配置文件的值。存放在OriginTrackedMapPropertySource中的map中。
        OriginTrackedMapPropertySource zookeeperSource = new OriginTrackedMapPropertySource(zkPropertyName, map);
        //把这个属性添加到spring容器中。
        propertySources.addLast(zookeeperSource);
    }

    /**
     * spring中所有的属性对象
     * 一个配置文件一个实体吗。
     * @return
     */
    private boolean checkExistsSpringProperty() {
        MutablePropertySources propertySources = applicationContext.getEnvironment().getPropertySources();
        for (PropertySource<?> propertySource : propertySources) {
            /**
             * 每一个propertySource就是一个配置文件
             */
            if (zkPropertyName.equals(propertySource.getName())) {
                return true;
            }
        }
        return false;
    }

    private void childNodeCache(CuratorFramework client, String path) {
        try {
            final PathChildrenCache pathChildrenCache = new PathChildrenCache(client, path, false);
            pathChildrenCache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);


            //堆节点的订阅通知接口。有修改。这个就被触发。
            pathChildrenCache.getListenable().addListener(new PathChildrenCacheListener() {
                @Override
                public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {


                    //调整Environment对象的值。

                    switch (event.getType()) {
                        case CHILD_ADDED:
                            System.out.println("增加了节点");
                            addEnv(event.getData(), client);
                            break;
                        case CHILD_REMOVED:
                            System.out.println("删除了节点");
                            delEnv(event.getData());
                            break;
                        case CHILD_UPDATED:
                            System.out.println("更新了节点");
                            addEnv(event.getData(), client);
                            break;
                        default:
                            break;
                    }
                    //对refresh作用域的实例进行刷新
                    //堆添加了@scope的类，进行重新的初始化
                    refreshBean();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshBean() {
        String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();
        for (String beanDefinitionName : beanDefinitionNames) {
            BeanDefinition beanDefinition = beanDefinitionRegistry.getBeanDefinition(beanDefinitionName);
            if(scopeName.equals(beanDefinition.getScope())) {
                //先删除,,,,思考，如果这时候删除了bean，有没有问题？
                //这个方法的调用，就会把把我们的添加了注解的类的@scope配置的自定义RefreshScope类，执行他的remove方法。
                applicationContext.getBeanFactory().destroyScopedBean(beanDefinitionName);
                //再重新的实例化初始化每一个bean
                /**
                 * 这个需要在这儿就实例化吗。？
                 *
                 * 比如controller。在mvc框架，会获取handler。也会有一个getbean的操作。
                 * 但是在一个service里面有一个@value。同时这个类，又被其他service有引用。就需要使用这个了。不然就会报null指针异常了。因为这个对象在map中被清除。可能就会被gc掉。
                 */
                applicationContext.getBean(beanDefinitionName);
            }
        }
    }

    private void delEnv(ChildData childData) {
        ChildData next = childData;
        String childpath = next.getPath();
        MutablePropertySources propertySources = applicationContext.getEnvironment().getPropertySources();
        for (PropertySource<?> propertySource : propertySources) {
            if (zkPropertyName.equals(propertySource.getName())) {
                OriginTrackedMapPropertySource ps = (OriginTrackedMapPropertySource) propertySource;
                ConcurrentHashMap chm = (ConcurrentHashMap) ps.getSource();
                chm.remove(childpath.substring(path.length() + 1));
            }
        }
    }

    private void addEnv(ChildData childData, CuratorFramework client) {
        ChildData next = childData;
        String childpath = next.getPath();
        String data = null;
        try {
            data = new String(client.getData().forPath(childpath));
        } catch (Exception e) {
            e.printStackTrace();
        }
        MutablePropertySources propertySources = applicationContext.getEnvironment().getPropertySources();
        for (PropertySource<?> propertySource : propertySources) {
            if (zkPropertyName.equals(propertySource.getName())) {
                OriginTrackedMapPropertySource ps = (OriginTrackedMapPropertySource) propertySource;
                ConcurrentHashMap chm = (ConcurrentHashMap) ps.getSource();
                chm.put(childpath.substring(path.length() + 1), data);
            }
        }
    }

    private void nodeCache(final CuratorFramework client, final String path) {

        try {
            //第三个参数是是否压缩
            //就是对path节点进行监控，是一个事件模板
            final NodeCache nodeCache = new NodeCache(client, path, false);
            nodeCache.start();

            //这个就是事件注册
            nodeCache.getListenable().addListener(new NodeCacheListener() {
                @Override
                public void nodeChanged() throws Exception {
                    byte[] data = nodeCache.getCurrentData().getData();
                    String path1 = nodeCache.getCurrentData().getPath();

                    Object put = map.put(path1.replace("/", ""), new String(data));
                    MutablePropertySources propertySources = applicationContext.getEnvironment().getPropertySources();
                    OriginTrackedMapPropertySource zookeeperSource = new OriginTrackedMapPropertySource("zookeeper source", map);
                    propertySources.addLast(zookeeperSource);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        CuratorUtil.applicationContext = (ConfigurableApplicationContext) context;
    }
}
