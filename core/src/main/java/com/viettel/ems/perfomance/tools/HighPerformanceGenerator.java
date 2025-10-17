package com.viettel.ems.perfomance.tools;

import com.viettel.ems.perfomance.config.SystemType;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.capnproto.MessageBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HighPerformanceGenerator {

    // ===================================================================================
    // ===                           BẢNG ĐIỀU KHIỂN CẤU HÌNH                           ===
    // ===================================================================================

    // --- Cấu hình FTP Server ---
    private static final String FTP_HOST = "localhost";
    private static final int FTP_PORT = 21;
    private static final String FTP_USER = "ftpuser";
    private static final String FTP_PASS = "nam456";

    private static final List<String> FTP_PATHS = List.of(
            "/Access/5GA/Others/ClickHouse",
            "/Access/5GA/Default/ClickHouse"
    );

    // --- Cấu hình Tạo File ---
    private static final SystemType SYSTEM_TYPE = SystemType.SYSTEM_5GA;
    private static final int TOTAL_FILES_TO_GENERATE = 10000;
    private static final int RECORDS_PER_FILE = 15000;

    // --- Cấu hình Hiệu Năng ---
    private static final int PARALLEL_THREADS = 20;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    // ===================================================================================

    private static final ThreadLocal<FTPClient> ftpClientThreadLocal = new ThreadLocal<>();
    private static final ConcurrentHashMap<String, Boolean> createdPaths = new ConcurrentHashMap<>();

    private static final int DURATION_SECONDS = 900;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss_SSS")
            .withLocale(Locale.US)
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_THREADS);
        AtomicInteger filesCompleted = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        System.out.printf("🚀 Bắt đầu tạo %d file với %d luồng song song...\n", TOTAL_FILES_TO_GENERATE, PARALLEL_THREADS);

        for (int i = 0; i < TOTAL_FILES_TO_GENERATE; i++) {
            final String targetFtpPath = FTP_PATHS.get(i % FTP_PATHS.size());
            final int fileIndex = i + 1;

            executor.submit(() -> {
                try {
                    String neName = String.format("gNodeB%04d", new Random().nextInt(10000));
                    byte[] fileData = createCapnprotoData(neName);
                    uploadWithRetry(targetFtpPath, neName, fileData);

                    int completed = filesCompleted.incrementAndGet();
                    if (completed % 100 == 0) {
                        System.out.printf("✅ Hoàn thành: %d / %d files\n", completed, TOTAL_FILES_TO_GENERATE);
                    }
                } catch (Exception e) {
                    System.err.printf("❌ BỎ CUỘC: Không thể xử lý file thứ %d. Lỗi cuối: %s\n", fileIndex, e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        ftpClientThreadLocal.remove();

        long endTime = System.currentTimeMillis();
        System.out.printf("\n🎉 Hoàn tất! Đã tạo và tải lên %d files trong %.2f giây.\n",
                filesCompleted.get(), (endTime - startTime) / 1000.0);
    }

    private static FTPClient getFtpClient() throws IOException {
        FTPClient ftpClient = ftpClientThreadLocal.get();
        if (ftpClient == null || !ftpClient.isConnected() || !ftpClient.sendNoOp()) {
            if (ftpClient != null) { try { ftpClient.disconnect(); } catch (IOException e) { /* ignore */ } }

            ftpClient = new FTPClient();
            ftpClient.connect(FTP_HOST, FTP_PORT);
            if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) throw new IOException("FTP server refused connection.");
            if (!ftpClient.login(FTP_USER, FTP_PASS)) throw new IOException("FTP login failed.");

            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
            ftpClient.enterLocalPassiveMode();
            ftpClientThreadLocal.set(ftpClient);
        }
        return ftpClient;
    }

    private static void ensureDirectoryExists(FTPClient ftpClient, String dirPath) throws IOException {
        String[] pathElements = dirPath.split("/");
        if (pathElements.length == 0) return;

        if (dirPath.startsWith("/")) {
            ftpClient.changeWorkingDirectory("/");
        }

        for (String singleDir : pathElements) {
            if (!singleDir.isEmpty()) {
                if (!ftpClient.changeWorkingDirectory(singleDir)) {
                    if (!ftpClient.makeDirectory(singleDir)) {
                        throw new IOException("Không thể tạo thư mục: " + singleDir + ". Phản hồi: " + ftpClient.getReplyString());
                    }
                    if (!ftpClient.changeWorkingDirectory(singleDir)) {
                        throw new IOException("Không thể đi vào thư mục vừa tạo: " + singleDir);
                    }
                }
            }
        }
    }

    private static void uploadWithRetry(String ftpPath, String neName, byte[] fileData) throws Exception {
        createdPaths.computeIfAbsent(ftpPath, path -> {
            try {
                System.out.println("🔎 Lần đầu tiên thấy đường dẫn '" + path + "', đang kiểm tra/tạo thư mục...");
                ensureDirectoryExists(getFtpClient(), path);

                // === THAY ĐỔI DUY NHẤT: TẠO THÊM THƯ MỤC "Done" ===
                String donePath = path.endsWith("/") ? (path + "Done") : (path + "/Done");
                ensureDirectoryExists(getFtpClient(), donePath);
                // ===============================================

                System.out.println("👍 Thư mục '" + path + "' và subfolder 'Done' đã sẵn sàng.");
                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        String timestampStr = TIME_FORMATTER.format(Instant.now());
        String finalFileName = String.format("%s_%s_%d.capnproto", neName, timestampStr, new Random().nextInt(1000));
        String remoteFilePath = ftpPath.endsWith("/") ? (ftpPath + finalFileName) : (ftpPath + "/" + finalFileName);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (InputStream inputStream = new ByteArrayInputStream(fileData)) {
                FTPClient ftpClient = getFtpClient();
                ftpClient.changeWorkingDirectory("/");
                if (ftpClient.storeFile(remoteFilePath, inputStream)) {
                    return;
                }
                throw new IOException("FTP server trả về false khi storeFile. Phản hồi: " + ftpClient.getReplyString());
            } catch (Exception e) {
                System.err.printf("    ⚠️ Lỗi upload lần %d cho file %s: %s. Thử lại sau %.1f giây...\n", attempt, finalFileName, e.getMessage(), RETRY_DELAY_MS / 1000.0);
                ftpClientThreadLocal.remove();
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
    }

    private static byte[] createCapnprotoData(String neName) throws IOException {
        MessageBuilder message = new MessageBuilder();
        var root = message.initRoot(CounterSchema.CounterDataCollection.factory);
        var dataList = root.initData(RECORDS_PER_FILE);
        Random random = new Random();
        long nowMillis = System.currentTimeMillis();

        for (int i = 0; i < RECORDS_PER_FILE; i++) {
            var counterData = dataList.get(i);
            long recordTimestamp = nowMillis - (long) i * DURATION_SECONDS * 1000L;
            counterData.setTime(recordTimestamp);
            counterData.setDuration(DURATION_SECONDS);

            String nodeFunction = pickNodeFunction(SYSTEM_TYPE, random);
            long cellId = random.nextInt(1, 4);
            String location = String.format("ManagedElement=%s,NodeFunction=%s,CellId=%d", neName, nodeFunction, cellId);

            counterData.setLocation(location);
            counterData.setCell(cellId);
            counterData.setService(1);

            var values = counterData.initData(2);
            values.get(0).setId(68);
            values.get(0).setValue(100 + random.nextInt(50000));
            values.get(1).setId(69);
            values.get(1).setValue(100 + random.nextInt(50000));
        }

        root.setUnit(neName);
        root.setType(0);

        ByteBuffer[] segments = message.getSegmentsForOutput();
        int totalSize = 0;
        for (ByteBuffer segment : segments) totalSize += segment.remaining();
        byte[] messageBytes = new byte[totalSize];
        int offset = 0;
        for (ByteBuffer segment : segments) {
            int remaining = segment.remaining();
            segment.get(messageBytes, offset, remaining);
            offset += remaining;
        }
        return messageBytes;
    }

    private static String pickNodeFunction(SystemType systemType, Random random) {
        if (systemType == null) return "1";
        return switch (systemType) {
            case SYSTEM_5GA -> {
                String[] values = {"1", "NR_FDD", "NR_TDD"};
                yield values[random.nextInt(values.length)];
            }
            case SYSTEM_4GA -> {
                String[] values = {"LTE_FDD", "LTE_TDD"};
                yield values[random.nextInt(values.length)];
            }
            default -> "1";
        };
    }
}