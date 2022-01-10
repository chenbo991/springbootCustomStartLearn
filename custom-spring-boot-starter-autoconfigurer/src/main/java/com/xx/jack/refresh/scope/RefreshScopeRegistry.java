package com.xx.jack.refresh.scope;

import lombok.Data;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;


/**
 * 实现了BeanDefinitionRegistryPostProcessor接口。会在spring启动的前期被调用
 * 把这个scope注解的value值 refresh对应的是哪一个RefreshScope。建立关系。方便在源码的时候，调用这个RefreshScope的get方法。
 */
@Data
@Component
public class RefreshScopeRegistry implements BeanDefinitionRegistryPostProcessor {

    private BeanDefinitionRegistry beanDefinitionRegistry;


    @Override
    public void postProcessBeanDefinitionRegistry(
        BeanDefinitionRegistry registry) throws BeansException {
        this.beanDefinitionRegistry = registry;
    }

    /**
     * 把这个scope注解的value值 refresh对应的是哪一个RefreshScope。建立关系。方便在源码的时候，调用这个RefreshScope的get方法。

     这个方法，会在启动spring实例化bean之前调用。完善beanFactory的属性。我们在这里把scopes添加我们自己的scope：RefreshScope
     * @param beanFactory
     * @throws BeansException
     */
    @Override
    public void postProcessBeanFactory(
        ConfigurableListableBeanFactory beanFactory) throws BeansException {
        beanFactory.registerScope("refresh",new RefreshScope());
    }
}
