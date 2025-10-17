package com.viettel.ems.perfomance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SystemConfig {
    @Value("${spring.pm.pool_size}")
    private int poolSize;

    @Value("${spring.pm.precounter_queue_size}")
    private int preCounterQueueSize;

    @Value("${spring.pm.threshold_warning_queue_size}")
    private int thresholdWarningQueueSize;

    @Value("${spring.ipha.host}")
    private String IPHA;

    @Value("${spring.ftp.folder_name_done}")
    private String folderNameFTPDone;
    @Value("${spring.ftp.folder_name_clickhouse}")
    private String folderNameFTPClickhouse;

    @Value("${spring.pm.limit-maximum-file}")
    private int limitMaximumFile;

    @Value("${spring.pm.limit-maximum-file-clickhouse}")
    private int limitMaximumFileClickhouse;


    @Value("${spring.pm.cache-interval}")
    private String intervalListStr;

    public SystemConfig() {
    }

    public int getPoolSize() {
        return this.poolSize;
    }

    public int getPreCounterQueueSize() {
        return this.preCounterQueueSize;
    }

    public int getThresholdWarningQueueSize() {
        return this.thresholdWarningQueueSize;
    }

    public String getIPHA() {
        return this.IPHA;
    }

    public String getFolderNameFTPDone() {
        return this.folderNameFTPDone;
    }

    public String getFolderNameFTPClickhouse() {
        return this.folderNameFTPClickhouse;
    }

    public int getLimitMaximumFile() {
        return this.limitMaximumFile;
    }

    public int getLimitMaximumFileClickhouse() {
        return this.limitMaximumFileClickhouse;
    }

    public String getIntervalListStr() {
        return this.intervalListStr;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public void setPreCounterQueueSize(int preCounterQueueSize) {
        this.preCounterQueueSize = preCounterQueueSize;
    }

    public void setThresholdWarningQueueSize(int thresholdWarningQueueSize) {
        this.thresholdWarningQueueSize = thresholdWarningQueueSize;
    }

    public void setIPHA(String IPHA) {
        this.IPHA = IPHA;
    }

    public void setFolderNameFTPDone(String folderNameFTPDone) {
        this.folderNameFTPDone = folderNameFTPDone;
    }

    public void setFolderNameFTPClickhouse(String folderNameFTPClickhouse) {
        this.folderNameFTPClickhouse = folderNameFTPClickhouse;
    }

    public void setLimitMaximumFile(int limitMaximumFile) {
        this.limitMaximumFile = limitMaximumFile;
    }

    public void setLimitMaximumFileClickhouse(int limitMaximumFileClickhouse) {
        this.limitMaximumFileClickhouse = limitMaximumFileClickhouse;
    }

    public void setIntervalListStr(String intervalListStr) {
        this.intervalListStr = intervalListStr;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof SystemConfig)) return false;
        final SystemConfig other = (SystemConfig) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.getPoolSize() != other.getPoolSize()) return false;
        if (this.getPreCounterQueueSize() != other.getPreCounterQueueSize()) return false;
        if (this.getThresholdWarningQueueSize() != other.getThresholdWarningQueueSize()) return false;
        final Object this$IPHA = this.getIPHA();
        final Object other$IPHA = other.getIPHA();
        if (this$IPHA == null ? other$IPHA != null : !this$IPHA.equals(other$IPHA)) return false;
        final Object this$folderNameFTPDone = this.getFolderNameFTPDone();
        final Object other$folderNameFTPDone = other.getFolderNameFTPDone();
        if (this$folderNameFTPDone == null ? other$folderNameFTPDone != null : !this$folderNameFTPDone.equals(other$folderNameFTPDone))
            return false;
        final Object this$folderNameFTPClickhouse = this.getFolderNameFTPClickhouse();
        final Object other$folderNameFTPClickhouse = other.getFolderNameFTPClickhouse();
        if (this$folderNameFTPClickhouse == null ? other$folderNameFTPClickhouse != null : !this$folderNameFTPClickhouse.equals(other$folderNameFTPClickhouse))
            return false;
        if (this.getLimitMaximumFile() != other.getLimitMaximumFile()) return false;
        if (this.getLimitMaximumFileClickhouse() != other.getLimitMaximumFileClickhouse()) return false;
        final Object this$intervalListStr = this.getIntervalListStr();
        final Object other$intervalListStr = other.getIntervalListStr();
        if (this$intervalListStr == null ? other$intervalListStr != null : !this$intervalListStr.equals(other$intervalListStr))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof SystemConfig;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.getPoolSize();
        result = result * PRIME + this.getPreCounterQueueSize();
        result = result * PRIME + this.getThresholdWarningQueueSize();
        final Object $IPHA = this.getIPHA();
        result = result * PRIME + ($IPHA == null ? 43 : $IPHA.hashCode());
        final Object $folderNameFTPDone = this.getFolderNameFTPDone();
        result = result * PRIME + ($folderNameFTPDone == null ? 43 : $folderNameFTPDone.hashCode());
        final Object $folderNameFTPClickhouse = this.getFolderNameFTPClickhouse();
        result = result * PRIME + ($folderNameFTPClickhouse == null ? 43 : $folderNameFTPClickhouse.hashCode());
        result = result * PRIME + this.getLimitMaximumFile();
        result = result * PRIME + this.getLimitMaximumFileClickhouse();
        final Object $intervalListStr = this.getIntervalListStr();
        result = result * PRIME + ($intervalListStr == null ? 43 : $intervalListStr.hashCode());
        return result;
    }

    public String toString() {
        return "SystemConfig(poolSize=" + this.getPoolSize() + ", preCounterQueueSize=" + this.getPreCounterQueueSize() + ", thresholdWarningQueueSize=" + this.getThresholdWarningQueueSize() + ", IPHA=" + this.getIPHA() + ", folderNameFTPDone=" + this.getFolderNameFTPDone() + ", folderNameFTPClickhouse=" + this.getFolderNameFTPClickhouse() + ", limitMaximumFile=" + this.getLimitMaximumFile() + ", limitMaximumFileClickhouse=" + this.getLimitMaximumFileClickhouse() + ", intervalListStr=" + this.getIntervalListStr() + ")";
    }
}
