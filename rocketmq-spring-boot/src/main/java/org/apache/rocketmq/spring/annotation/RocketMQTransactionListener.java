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

package org.apache.rocketmq.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * This annotation is used over a class which implements interface
 * org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener, which will be converted to
 * org.apache.rocketmq.client.producer.TransactionListener later. The class implements
 * two methods for process callback events after the txProducer sends a transactional message.
 * <p>Note: The annotation is used only on RocketMQ client producer side, it can not be used
 * on consumer side.
 *
 * 此注解用于实现接口 org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener 的类，
 * 稍后将转换为 org.apache.rocketmq.client.producer.TransactionListener。
 * 在 txProducer 发送事务消息后，该类实现了两个处理回调事件的方法。
 * 注意：注解只在RocketMQ客户端生产者端使用，消费者端不能使用。
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface RocketMQTransactionListener {

    /**
     * Set ExecutorService params -- corePoolSize
     */
    int corePoolSize() default 1;

    /**
     * Set ExecutorService params -- maximumPoolSize
     */
    int maximumPoolSize() default 1;

    /**
     * Set ExecutorService params -- keepAliveTime
     */
    long keepAliveTime() default 1000 * 60;

    /**
     * Set ExecutorService params -- keepAliveTimeUnit
     */
    TimeUnit keepAliveTimeUnit() default TimeUnit.MILLISECONDS;

    /**
     * Set ExecutorService params -- blockingQueueSize
     */
    int blockingQueueSize() default 2000;

    /**
     * Set rocketMQTemplate bean name, the default is rocketMQTemplate.
     * if use ExtRocketMQTemplate, can set ExtRocketMQTemplate bean name.
     */
    String rocketMQTemplateBeanName() default "rocketMQTemplate";

}
