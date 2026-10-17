package com.ruoyi.officecare.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OfficeCare文档任务RabbitMQ配置。
 *
 * @author officecare
 */
@Component
@ConfigurationProperties(prefix = "officecare.messaging.rabbit")
public class OfficeCareRabbitProperties {
    private boolean enabled;

    private String exchange = "officecare.document.exchange";

    private String deadExchange = "officecare.document.dead.exchange";

    private String parseQueue = "officecare.document.parse.queue";

    private String indexQueue = "officecare.document.index.queue";

    private String deleteIndexQueue = "officecare.document.delete-index.queue";

    private String deadQueue = "officecare.document.dead.queue";

    private String queueType = "quorum";

    private int consumerConcurrency = 1;

    private int prefetchCount = 1;

    private int outboxBatchSize = 20;

    private long publisherConfirmTimeoutMs = 5000L;

    private int maxPublishAttempts = 10;

    private long retryInitialDelaySeconds = 30L;

    private long retryMaxDelaySeconds = 600L;

    private long publishingStaleSeconds = 300L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = requireText(exchange, "exchange");
    }

    public String getDeadExchange() {
        return deadExchange;
    }

    public void setDeadExchange(String deadExchange) {
        this.deadExchange = requireText(deadExchange, "dead-exchange");
    }

    public String getParseQueue() {
        return parseQueue;
    }

    public void setParseQueue(String parseQueue) {
        this.parseQueue = requireText(parseQueue, "parse-queue");
    }

    public String getIndexQueue() {
        return indexQueue;
    }

    public void setIndexQueue(String indexQueue) {
        this.indexQueue = requireText(indexQueue, "index-queue");
    }

    public String getDeleteIndexQueue() {
        return deleteIndexQueue;
    }

    public void setDeleteIndexQueue(String deleteIndexQueue) {
        this.deleteIndexQueue = requireText(deleteIndexQueue, "delete-index-queue");
    }

    public String getDeadQueue() {
        return deadQueue;
    }

    public void setDeadQueue(String deadQueue) {
        this.deadQueue = requireText(deadQueue, "dead-queue");
    }

    public String getQueueType() {
        return queueType;
    }

    public void setQueueType(String queueType) {
        String value = requireText(queueType, "queue-type").toLowerCase();
        if (!"classic".equals(value) && !"quorum".equals(value)) {
            throw new IllegalArgumentException(
                    "officecare.messaging.rabbit.queue-type必须是classic或quorum");
        }
        this.queueType = value;
    }

    public int getConsumerConcurrency() {
        return consumerConcurrency;
    }

    public void setConsumerConcurrency(int consumerConcurrency) {
        this.consumerConcurrency = requireRange(
                consumerConcurrency, 1, 8, "consumer-concurrency");
    }

    public int getPrefetchCount() {
        return prefetchCount;
    }

    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = requireRange(prefetchCount, 1, 20, "prefetch-count");
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = requireRange(outboxBatchSize, 1, 100,
                "outbox-batch-size");
    }

    public long getPublisherConfirmTimeoutMs() {
        return publisherConfirmTimeoutMs;
    }

    public void setPublisherConfirmTimeoutMs(long publisherConfirmTimeoutMs) {
        this.publisherConfirmTimeoutMs = requireRange(
                publisherConfirmTimeoutMs, 500L, 30000L,
                "publisher-confirm-timeout-ms");
    }

    public int getMaxPublishAttempts() {
        return maxPublishAttempts;
    }

    public void setMaxPublishAttempts(int maxPublishAttempts) {
        this.maxPublishAttempts = requireRange(maxPublishAttempts, 1, 50,
                "max-publish-attempts");
    }

    public long getRetryInitialDelaySeconds() {
        return retryInitialDelaySeconds;
    }

    public void setRetryInitialDelaySeconds(long retryInitialDelaySeconds) {
        this.retryInitialDelaySeconds = requireRange(
                retryInitialDelaySeconds, 1L, 3600L,
                "retry-initial-delay-seconds");
    }

    public long getRetryMaxDelaySeconds() {
        return retryMaxDelaySeconds;
    }

    public void setRetryMaxDelaySeconds(long retryMaxDelaySeconds) {
        this.retryMaxDelaySeconds = requireRange(
                retryMaxDelaySeconds, 1L, 86400L,
                "retry-max-delay-seconds");
    }

    public long getPublishingStaleSeconds() {
        return publishingStaleSeconds;
    }

    public void setPublishingStaleSeconds(long publishingStaleSeconds) {
        this.publishingStaleSeconds = requireRange(
                publishingStaleSeconds, 30L, 3600L,
                "publishing-stale-seconds");
    }

    public long retryDelaySeconds(int completedAttempts) {
        int exponent = Math.max(0, Math.min(completedAttempts - 1, 20));
        long multiplier = 1L << exponent;
        long delay;
        try {
            delay = Math.multiplyExact(retryInitialDelaySeconds, multiplier);
        } catch (ArithmeticException exception) {
            delay = retryMaxDelaySeconds;
        }
        return Math.min(delay, retryMaxDelaySeconds);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "officecare.messaging.rabbit." + name + "不能为空");
        }
        return value.trim();
    }

    private static int requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "officecare.messaging.rabbit." + name + "必须在"
                            + min + "到" + max + "之间");
        }
        return value;
    }

    private static long requireRange(long value, long min, long max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "officecare.messaging.rabbit." + name + "必须在"
                            + min + "到" + max + "之间");
        }
        return value;
    }
}
