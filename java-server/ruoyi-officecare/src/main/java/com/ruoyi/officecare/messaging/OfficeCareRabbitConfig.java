package com.ruoyi.officecare.messaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OfficeCare文档任务Exchange、Queue和死信拓扑。
 *
 * @author officecare
 */
@Configuration
@EnableRabbit
@EnableScheduling
@ConditionalOnProperty(prefix = "officecare.messaging.rabbit", name = "enabled",
    havingValue = "true")
public class OfficeCareRabbitConfig
{
    @Bean
    public Declarables officeCareDocumentTaskTopology(OfficeCareRabbitProperties properties)
    {
        DirectExchange exchange = new DirectExchange(properties.getExchange(), true, false);
        DirectExchange deadExchange = new DirectExchange(
            properties.getDeadExchange(), true, false);

        Queue parseQueue = businessQueue(properties.getParseQueue(), properties);
        Queue indexQueue = businessQueue(properties.getIndexQueue(), properties);
        Queue deleteQueue = businessQueue(properties.getDeleteIndexQueue(), properties);
        Queue deadQueue = QueueBuilder.durable(properties.getDeadQueue())
            .withArgument("x-queue-type", properties.getQueueType())
            .build();

        List<Declarable> declarables = new ArrayList<>();
        declarables.add(exchange);
        declarables.add(deadExchange);
        declarables.add(parseQueue);
        declarables.add(indexQueue);
        declarables.add(deleteQueue);
        declarables.add(deadQueue);
        declarables.add(binding(parseQueue, exchange,
            DocumentTaskRouting.PARSE_ROUTING_KEY));
        declarables.add(binding(indexQueue, exchange,
            DocumentTaskRouting.INDEX_ROUTING_KEY));
        declarables.add(binding(deleteQueue, exchange,
            DocumentTaskRouting.DELETE_INDEX_ROUTING_KEY));
        declarables.add(binding(deadQueue, deadExchange,
            DocumentTaskRouting.DEAD_ROUTING_KEY));
        return new Declarables(declarables);
    }

    private Queue businessQueue(String name, OfficeCareRabbitProperties properties)
    {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-queue-type", properties.getQueueType());
        arguments.put("x-dead-letter-exchange", properties.getDeadExchange());
        arguments.put("x-dead-letter-routing-key", DocumentTaskRouting.DEAD_ROUTING_KEY);
        return new Queue(name, true, false, false, arguments);
    }

    private Binding binding(Queue queue, DirectExchange exchange, String routingKey)
    {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
