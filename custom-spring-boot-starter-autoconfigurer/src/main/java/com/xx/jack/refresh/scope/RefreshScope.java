package com.xx.jack.refresh.scope;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;


/**
 * 自定义一个scope。用于spring初始化bean的时候，保存再自定义的容器集合中。ConcurrentHashMap
 */
public class RefreshScope implements Scope {

    private ConcurrentHashMap beanMap = new ConcurrentHashMap();

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        if(beanMap.containsKey(name)) {
            return beanMap.get(name);
        }

        Object object = objectFactory.getObject();
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
