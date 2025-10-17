package com.viettel.ems.perfomance.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * FTP Connection Pool Configuration
 * Reuses FTP connections to reduce overhead (TCP handshake, authentication)
 *
 * Performance: Without pool = 1000 files = 1000 connections
 *              With pool    = 1000 files = 20 connections reused
 */
@Configuration
@Slf4j
public class FtpConnectionPoolConfig {

    @Value("${ftp.pool.max-total:20}")
    private int maxTotal;

    @Value("${ftp.pool.max-idle:10}")
    private int maxIdle;

    @Value("${ftp.pool.min-idle:5}")
    private int minIdle;

    @Value("${ftp.pool.max-wait-millis:30000}")
    private long maxWaitMillis;

    @Value("${ftp.default.host:localhost}")
    private String defaultFtpHost;

    @Value("${ftp.default.port:21}")
    private int defaultFtpPort;

    @Value("${ftp.default.username:anonymous}")
    private String defaultFtpUsername;

    @Value("${ftp.default.password:}")
    private String defaultFtpPassword;

    @Bean
    public GenericObjectPool<FTPClient> ftpClientPool() {
        GenericObjectPoolConfig<FTPClient> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setMaxWaitMillis(maxWaitMillis);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setTestWhileIdle(true);
        config.setTimeBetweenEvictionRunsMillis(60000); // 1 minute
        config.setMinEvictableIdleTimeMillis(300000); // 5 minutes
        config.setBlockWhenExhausted(true);
        config.setJmxEnabled(true);
        config.setJmxNamePrefix("FtpClientPool");

        FtpClientFactory factory = new FtpClientFactory(
            defaultFtpHost,
            defaultFtpPort,
            defaultFtpUsername,
            defaultFtpPassword
        );

        GenericObjectPool<FTPClient> pool = new GenericObjectPool<>(factory, config);

        log.info("FTP Connection Pool initialized: maxTotal={}, maxIdle={}, minIdle={}",
            maxTotal, maxIdle, minIdle);

        return pool;
    }

    /**
     * Factory to create and manage FTP client lifecycle
     */
    @Slf4j
    static class FtpClientFactory extends BasePooledObjectFactory<FTPClient> {

        private final String host;
        private final int port;
        private final String username;
        private final String password;

        public FtpClientFactory(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        @Override
        public FTPClient create() throws Exception {
            FTPClient ftpClient = new FTPClient();

            try {
                // Configure
                FTPClientConfig config = new FTPClientConfig();
                config.setServerTimeZoneId("UTC");
                ftpClient.configure(config);
                ftpClient.setControlEncoding("UTF-8");
                ftpClient.setAutodetectUTF8(true);
                ftpClient.setBufferSize(1048576); // 1MB buffer
                ftpClient.setConnectTimeout(30000); // 30s
                ftpClient.setDataTimeout(60000); // 60s
                ftpClient.setDefaultTimeout(60000); // 60s

                // Connect
                ftpClient.connect(host, port);

                int reply = ftpClient.getReplyCode();
                if (!FTPReply.isPositiveCompletion(reply)) {
                    ftpClient.disconnect();
                    throw new IOException("FTP server refused connection. Reply: " + reply);
                }

                // Login
                if (!ftpClient.login(username, password)) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                    throw new IOException("FTP login failed for user: " + username);
                }

                // Set passive mode and binary
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
                ftpClient.setRemoteVerificationEnabled(false);

                log.debug("Created new FTP client connection to {}:{}", host, port);
                return ftpClient;

            } catch (Exception e) {
                if (ftpClient.isConnected()) {
                    try {
                        ftpClient.disconnect();
                    } catch (IOException ex) {
                        // Ignore
                    }
                }
                throw e;
            }
        }

        @Override
        public PooledObject<FTPClient> wrap(FTPClient ftpClient) {
            return new DefaultPooledObject<>(ftpClient);
        }

        @Override
        public void destroyObject(PooledObject<FTPClient> p) throws Exception {
            FTPClient ftpClient = p.getObject();
            if (ftpClient != null && ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                    ftpClient.disconnect();
                    log.debug("Destroyed FTP client connection");
                } catch (IOException e) {
                    log.error("Error destroying FTP client", e);
                }
            }
        }

        @Override
        public boolean validateObject(PooledObject<FTPClient> p) {
            FTPClient ftpClient = p.getObject();
            try {
                // Check if connected and send NOOP command
                if (ftpClient == null || !ftpClient.isConnected()) {
                    return false;
                }

                // Send NOOP to verify connection is alive
                return ftpClient.sendNoOp();

            } catch (IOException e) {
                log.warn("FTP client validation failed", e);
                return false;
            }
        }

        @Override
        public void activateObject(PooledObject<FTPClient> p) throws Exception {
            FTPClient ftpClient = p.getObject();
            // Reset to passive mode and root directory
            if (!ftpClient.getReplyString().contains("PASV")) {
                ftpClient.enterLocalPassiveMode();
            }
        }

        @Override
        public void passivateObject(PooledObject<FTPClient> p) throws Exception {
            FTPClient ftpClient = p.getObject();
            // Complete any pending commands before returning to pool
            try {
                ftpClient.completePendingCommand();
            } catch (IOException e) {
                log.warn("Error completing pending command", e);
            }
        }
    }
}
