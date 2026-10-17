package com.ruoyi.officecare.domain.vo;

/** 工作台展示的RabbitMQ文档任务运行指标。 */
public class OfficeCareMqMetrics
{
    private boolean enabled;

    private long pendingTaskCount;

    private long runningTaskCount;

    private long failedTaskCount;

    private long deadLetterCount;

    private long taskRetryCount;

    private long publishRetryCount;

    private long averageWaitTimeMs;

    private long averageProcessTimeMs;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public long getPendingTaskCount()
    {
        return pendingTaskCount;
    }

    public void setPendingTaskCount(long pendingTaskCount)
    {
        this.pendingTaskCount = pendingTaskCount;
    }

    public long getRunningTaskCount()
    {
        return runningTaskCount;
    }

    public void setRunningTaskCount(long runningTaskCount)
    {
        this.runningTaskCount = runningTaskCount;
    }

    public long getFailedTaskCount()
    {
        return failedTaskCount;
    }

    public void setFailedTaskCount(long failedTaskCount)
    {
        this.failedTaskCount = failedTaskCount;
    }

    public long getDeadLetterCount()
    {
        return deadLetterCount;
    }

    public void setDeadLetterCount(long deadLetterCount)
    {
        this.deadLetterCount = deadLetterCount;
    }

    public long getTaskRetryCount()
    {
        return taskRetryCount;
    }

    public void setTaskRetryCount(long taskRetryCount)
    {
        this.taskRetryCount = taskRetryCount;
    }

    public long getPublishRetryCount()
    {
        return publishRetryCount;
    }

    public void setPublishRetryCount(long publishRetryCount)
    {
        this.publishRetryCount = publishRetryCount;
    }

    public long getAverageWaitTimeMs()
    {
        return averageWaitTimeMs;
    }

    public void setAverageWaitTimeMs(long averageWaitTimeMs)
    {
        this.averageWaitTimeMs = averageWaitTimeMs;
    }

    public long getAverageProcessTimeMs()
    {
        return averageProcessTimeMs;
    }

    public void setAverageProcessTimeMs(long averageProcessTimeMs)
    {
        this.averageProcessTimeMs = averageProcessTimeMs;
    }
}
