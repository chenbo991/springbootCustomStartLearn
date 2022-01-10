package com.xx.jack.refresh.scope;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;


/**
 * 自定义一个scope。用于spring初始化bean的时候，保存再自定义的容器集合中。ConcurrentHashMap
 */
public class RefreshScope implements Scope {

    private ConcurrentHashMap beanMap = new ConcurrentHashMap();


    /**
     * 这儿get方法。会被spring在获取bean的时候调用。我们自己自定义获取bean
     * 1.先在自己先从我们的自定义容器中获取bean
     * 2.获取不到，就再调用createBean(beanName, mbd, args)创建bean.
     *
     * @param name
     * @param objectFactory
     * @return
     */
    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {

        /**
         * 先从我们的自定义容器中获取bean。
         */
        if(beanMap.containsKey(name)) {
            return beanMap.get(name);
        }

//没有获取到bean就新建bean
// return createBean(beanName, mbd, args)
        Object object = objectFactory.getObject();//创建bean.会调用
        beanMap.put(name,object);
        return object;
    }

    @Override
    public Object remove(String name) {
        return beanMap.remove(name);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {

    }

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return null;
    }
}
