/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.spring.autoconfigure;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.client.AccessChannel;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQReplyListener;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.apache.rocketmq.spring.support.RocketMQMessageConverter;
import org.apache.rocketmq.spring.support.RocketMQUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionValidationException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

@Configuration
public class ListenerContainerConfiguration implements ApplicationContextAware {
    private final static Logger log = LoggerFactory.getLogger(ListenerContainerConfiguration.class);

    private ConfigurableApplicationContext applicationContext;

    private AtomicLong counter = new AtomicLong(0);

    private ConfigurableEnvironment environment;

    private RocketMQProperties rocketMQProperties;

    private RocketMQMessageConverter rocketMQMessageConverter;

    /**
     * 构造器初始化 设置一些配置
     * @param rocketMQMessageConverter
     * @param environment
     * @param rocketMQProperties
     */
    public ListenerContainerConfiguration(RocketMQMessageConverter rocketMQMessageConverter,
        ConfigurableEnvironment environment, RocketMQProperties rocketMQProperties) {
        this.rocketMQMessageConverter = rocketMQMessageConverter;
        this.environment = environment;
        this.rocketMQProperties = rocketMQProperties;
    }

    /**
     * set 上下文
     * @param applicationContext the ApplicationContext object to be used by this object
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }

    /**
     * 注册rocketmq 的 容器
     * @param beanName
     * @param bean
     * @param annotation
     */
    public void registerContainer(String beanName, Object bean, RocketMQMessageListener annotation) {
        Class<?> clazz = AopProxyUtils.ultimateTargetClass(bean);

        if (RocketMQListener.class.isAssignableFrom(bean.getClass()) && RocketMQReplyListener.class.isAssignableFrom(bean.getClass())) {
            throw new IllegalStateException(clazz + " cannot be both instance of " + RocketMQListener.class.getName() + " and " + RocketMQReplyListener.class.getName());
        }

        if (!RocketMQListener.class.isAssignableFrom(bean.getClass()) && !RocketMQReplyListener.class.isAssignableFrom(bean.getClass())) {
            throw new IllegalStateException(clazz + " is not instance of " + RocketMQListener.class.getName() + " or " + RocketMQReplyListener.class.getName());
        }

        //解析和获取 消费者group
        String consumerGroup = this.environment.resolvePlaceholders(annotation.consumerGroup());
        //解析topic
        String topic = this.environment.resolvePlaceholders(annotation.topic());

        //监听是否开启，如果没有开启，下面直接就出去了。，这里一般都是true
        boolean listenerEnabled =
            (boolean) rocketMQProperties.getPushConsumer().getListeners().getOrDefault(consumerGroup, Collections.EMPTY_MAP)
                .getOrDefault(topic, true);

        if (!listenerEnabled) {
            log.debug(
                "Consumer Listener (group:{},topic:{}) is not enabled by configuration, will ignore initialization.",
                consumerGroup, topic);
            return;
        }
        //校验注解中的一些配置
        // 如果 消费模型是 顺序的，并且是广播的模式 ，直接就报错   BROADCASTING 不支持 顺序消费
        validate(annotation);

        //containerBeanName 生成方式，DefaultRocketMQListenerContainer_数字
        // DefaultRocketMQListenerContainer_1,DefaultRocketMQListenerContainer_2 .....
        String containerBeanName = String.format("%s_%s", DefaultRocketMQListenerContainer.class.getName(),
            counter.incrementAndGet());
        GenericApplicationContext genericApplicationContext = (GenericApplicationContext) applicationContext;

        //注册了一个bean
        genericApplicationContext.registerBean(containerBeanName, DefaultRocketMQListenerContainer.class,
            () -> createRocketMQListenerContainer(containerBeanName, bean, annotation));
        //注册完之后再次获取下，如果 这个容器 没有运行，在这里进行启动这个容器
        DefaultRocketMQListenerContainer container = genericApplicationContext.getBean(containerBeanName,
            DefaultRocketMQListenerContainer.class);
        if (!container.isRunning()) {
            try {
                container.start();
            } catch (Exception e) {
                log.error("Started container failed. {}", container, e);
                throw new RuntimeException(e);
            }
        }

        log.info("Register the listener to container, listenerBeanName:{}, containerBeanName:{}", beanName, containerBeanName);
    }

    /**
     * 注册 RocketMQListener容器 的过程
     * @param name
     * @param bean
     * @param annotation
     * @return
     */
    private DefaultRocketMQListenerContainer createRocketMQListenerContainer(String name, Object bean,
        RocketMQMessageListener annotation) {
        DefaultRocketMQListenerContainer container = new DefaultRocketMQListenerContainer();

        //设置 监听的一些信息
        container.setRocketMQMessageListener(annotation);

        //解析nameServer
        String nameServer = environment.resolvePlaceholders(annotation.nameServer());
        nameServer = StringUtils.isEmpty(nameServer) ? rocketMQProperties.getNameServer() : nameServer;
        //解析accessChannel 默认是 ${rocketmq.access-channel:} ，默认是空
        String accessChannel = environment.resolvePlaceholders(annotation.accessChannel());
        container.setNameServer(nameServer);
        if (!StringUtils.isEmpty(accessChannel)) {
            container.setAccessChannel(AccessChannel.valueOf(accessChannel));
        }
        container.setTopic(environment.resolvePlaceholders(annotation.topic()));
        String tags = environment.resolvePlaceholders(annotation.selectorExpression());
        if (!StringUtils.isEmpty(tags)) {
            container.setSelectorExpression(tags);
        }
        container.setConsumerGroup(environment.resolvePlaceholders(annotation.consumerGroup()));
        container.setTlsEnable(environment.resolvePlaceholders(annotation.tlsEnable()));

        //判断是 延迟队列监听还是 正常的消费队列，并且进行设置
        if (RocketMQListener.class.isAssignableFrom(bean.getClass())) {
            container.setRocketMQListener((RocketMQListener) bean);
        } else if (RocketMQReplyListener.class.isAssignableFrom(bean.getClass())) {
            container.setRocketMQReplyListener((RocketMQReplyListener) bean);
        }
        container.setMessageConverter(rocketMQMessageConverter.getMessageConverter());
        container.setName(name);

        String namespace = environment.resolvePlaceholders(annotation.namespace());
        container.setNamespace(RocketMQUtil.getNamespace(namespace,
            rocketMQProperties.getPushConsumer().getNamespace()));
        return container;
    }

    /**
     * 如果 消费模型是 顺序的，并且是广播的模式 ，直接就报错   BROADCASTING 不支持 顺序消费
     * @param annotation
     */
    private void validate(RocketMQMessageListener annotation) {
        if (annotation.consumeMode() == ConsumeMode.ORDERLY &&
            annotation.messageModel() == MessageModel.BROADCASTING) {
            throw new BeanDefinitionValidationException(
                "Bad annotation definition in @RocketMQMessageListener, messageModel BROADCASTING does not support ORDERLY message!");
        }
    }
}
