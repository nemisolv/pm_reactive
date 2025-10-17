//package com.viettel.ems.perfomance.service;
//
//import com.viettel.ems.perfomance.common.CounterFileHandler;
//import com.viettel.ems.perfomance.config.ContextAwareExecutor;
//import com.viettel.ems.perfomance.config.SystemType;
//import com.viettel.ems.perfomance.config.TenantContextHolder;
//import com.viettel.ems.perfomance.object.CounterDataObject;
//import com.viettel.ems.perfomance.object.FtpServerObject;
//import org.apache.commons.net.ftp.FTPClient;
//import org.apache.commons.net.ftp.FTPClientConfig;
//import org.apache.commons.net.ftp.FTPFile;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.net.ftp.FTPReply;
//
//import java.io.*;
//import java.nio.ByteBuffer;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicInteger;
//
//@Slf4j
//public class FtpServer {
//    private FTPClient ftpClient;
//    private final FtpServerObject ftpServerObject;
//    private static final int MAX_RETRy = 3;
//    private static final ConcurrentHashMap<String, Object> SERVER_LOCKS = new ConcurrentHashMap<>();
//    private final ContextAwareExecutor contextAwareExecutor; // <-- Thêm dòng này
//    // lock server, avoid "thundering herd"
//    private Object serverLock() {
//        return SERVER_LOCKS.computeIfAbsent(ftpServerObject.getKey(), k -> new Object());
//    }
//
//    public FtpServer(FtpServerObject ftpServerObject, ContextAwareExecutor contextAwareExecutor) {
//        this.ftpServerObject = ftpServerObject;
//        this.contextAwareExecutor = contextAwareExecutor;
//    }
//
//    public boolean connect(){
//        if(ftpClient == null) {
//            return connect(ftpServerObject);
//        }else if(ftpClient.isConnected()) {
//            disconnect();
//            return connect(ftpServerObject);
//        }else
//            return ftpClient.isAvailable();
//    }
//
//
//
//
//
//
//    public boolean connect(FtpServerObject ftpServerObject) {
//        ftpClient = new FTPClient();
//        try {
//            FTPClientConfig config = new FTPClientConfig();
//            config.setServerTimeZoneId("UTC");
//            ftpClient.configure(config);
//            ftpClient.setControlEncoding("UTF-8");
//            ftpClient.setAutodetectUTF8(true);
//            ftpClient.setBufferSize(1048576);
//            ftpClient.connect(ftpServerObject.getHost(), ftpServerObject.getPort());
//            ftpClient.enterLocalPassiveMode();
//            ftpClient.setRemoteVerificationEnabled(false);
//
//            if(!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
//                disconnect();
//                throw new IOException("FTP Server not response!");
//            }else {
//                ftpClient.setSoTimeout(60000);
//                if(!ftpClient.login(ftpServerObject.getUsername(), ftpServerObject.getPassword())) {
//                    throw new IOException("FTP Server login failed!");
//                }
//                ftpClient.setDataTimeout(60000);
//                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
//            }
//
//
//        }catch (Exception e){
//            log.error("FtpServer connect fail {}", e.getMessage());
//            return false;
//        }
//        return true;
//    }
//
//    public void disconnect() {
//        if (ftpClient != null && ftpClient.isConnected()) {
//           try {
//               ftpClient.logout();
//               ftpClient.disconnect();
//           }catch (Exception e){
//               log.error("FtpServer disconnect fail {}", e.getMessage());
//           }
//
//        }
//        ftpClient = null;
//        log.info("FtpServer disconnect success");
//    }
//
//
//
//    public boolean renameFile(String fromPath, String toPath) {
//        return renameFile(fromPath, toPath, 0 );
//    }
//
//
//    public void getListFile(String path, int limitMaximumFile, AtomicInteger totalFileRead, CounterFileHandler cfh) {
//        synchronized (serverLock()) {
//            getListFile(path, limitMaximumFile, totalFileRead, cfh, 0);
//        }
//    }
//
//    private void getListFile(String path, int limitMaximumFile, AtomicInteger totalFileRead, CounterFileHandler cfh, int retry ) {
//        try {
//                var ftpFiles = ftpClient.listFiles(path, ftpFile -> {
//                    try {
//                        return ftpFile.isFile();
//                    }catch (Exception e){
//                        log.error(e.getMessage(), e);
//                        return false;
//                    }
//                });
//                if(ftpFiles == null || ftpFiles.length == 0) {
//                    log.info("{} has 0 file", path);
//                    return;
//                }
//                log.info("{} has {} file(s)", path, ftpFiles.length);
//                int numOfReadFileCurrent = totalFileRead.get();
//                totalFileRead.set(totalFileRead.get() + ftpFiles.length);
//                int countFile = totalFileRead.get() > limitMaximumFile ? limitMaximumFile : totalFileRead.get();
//                log.info("{} will read {} file(s)", path, countFile - numOfReadFileCurrent );
//                for(int idx = 0;idx < countFile - numOfReadFileCurrent; idx++) {
//                    try {
//                        var ftpFile = ftpFiles[idx];
//                        var preCounter = readFile(path, ftpFile);
//                        cfh.onSuccess(preCounter);
//                    }catch (Exception e) {
//                        log.error(e.getMessage(), e);
//                    }
//                }
//
//        }catch (Exception e) {
//            log.error("getListFile error {}", e.getMessage());
//            if(retry < MAX_RETRy) {
//                log.info("{}-{}", retry, "RETRY CONNECT FTP SERVER");
//                reconnect();
//                getListFile(path, limitMaximumFile, totalFileRead, cfh, retry + 1);
//            }
//        }
//    }
//
//
//
//
//
//
//    public boolean renameFile(String fromPath, String toPath, int retry)  {
//
//
//        try {
//            ftpClient.rename(fromPath, toPath);
//            return true;
//        }catch (Exception e){
//        log.error("FtpServer rename fail {}", e.getMessage());
//        if(retry < MAX_RETRy) {
//            log.info("{}-{}", retry, "RETRY CONNECT FTP SERVER");
//            reconnect();
//            return renameFile(fromPath, toPath, retry + 1);
//        }
//        return false;
//        }
//
//    }
//
//    public CounterDataObject getFile(String path, String fileName) {
//        return getFile(path, fileName, 0);
//    }
//
//    private CounterDataObject getFile(String path, String fileName, int retry) {
//        try {
//            FTPFile[] ftpFiles = ftpClient.listFiles(path, ftpFile -> {
//                try {
//                    return ftpFile.isFile() && ftpFile.getName().equals(fileName);
//                }catch (Exception e){
//                    log.error(e.getMessage(), e);
//                    return false;
//                }
//            });
//            if(ftpFiles == null || ftpFiles.length == 0) {
//                log.info("{} has {} file", path, 0 );
//                return null;
//            }
//            return readFile(path, ftpFiles[0]);
//
//        }catch (Exception e){
//            log.error(e.getMessage(), e);
//            if(retry < MAX_RETRy) {
//                log.info("{}-{}", retry, "RETRY CONNECT FTP SERVER");
//                reconnect();
//                return getFile(path, fileName, retry + 1);
//            }
//            return null;
//        }
//    }
//
//
//
//
//
//
//
//    public void reconnect()  {
//        disconnect();
//        connect();
//    }
//
//    public void moveFile(String fromPath, String toPath) throws Exception {
//        int attempts = 0;
//        try {
//            boolean success = ftpClient.rename(fromPath, toPath);
//            if (!success) {
//                throw new Exception("Failed to move file from " + fromPath + " to " + toPath);
//            }
//        } catch (Exception e) {
//            // Retry logic
//            while (attempts++ < MAX_RETRy) {
//                reconnect();
//                boolean success = ftpClient.rename(fromPath, toPath);
//                if (success) return;
//            }
//            throw e;
//        }
//    }
//
//    public CounterDataObject readFile(String path, FTPFile ftpFile) throws Exception {
//        CounterDataObject preCounter = null;
//        InputStream inputStream = null;
//        try {
//            inputStream = ftpClient.retrieveFileStream(path + "/" + ftpFile.getName() );
//            String fileName = ftpFile.getName();
//            if(fileName.toUpperCase().contains("_GNODEB_") && fileName.toUpperCase().contains("RU")) {
//                List<String> lstLine = new ArrayList<>();
//                String line;
//                try(BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
//                    while((line = br.readLine()) != null) {
//                        lstLine.add(line);
//                    }
//                }catch (Exception e){
//                    log.error(e.getMessage(), e);
//                }
//                preCounter = new CounterDataObject(ftpServerObject, path,fileName, null, lstLine);
//            }else {
//                byte[] message = inputStream.readAllBytes();
//                ByteBuffer bufferData =  ByteBuffer.wrap(message);
//                log.debug("Capacity: {}, limit: {}", bufferData.capacity(), bufferData.limit());
//                preCounter = new CounterDataObject(ftpServerObject, path, fileName, bufferData, null);
//            }
//        }catch (Exception e) {
//            log.error(e.getMessage(), e);
//        }finally {
//            try {
//                if(inputStream != null && ftpClient != null && ftpClient.isConnected()) {
//                    inputStream.close();
//                    ftpClient.completePendingCommand();
//                }
//            }catch (Exception e){
//                log.error(e.getMessage(), e);
//            }
//        }
//        return preCounter;
//    }
//
//
//    public void getListFileClickHouse(String path, int limitMaximumFile, AtomicInteger totalFileRead, CounterFileHandler cfh) {
//        getListFileClickHouse(path, limitMaximumFile, totalFileRead, cfh, 0);
//    }
//
//    private void getListFileClickHouse(String path, int limitMaximumFile, AtomicInteger totalFileRead, CounterFileHandler cfh, int retry) {
//        try {
//            List<String> fileNames = new ArrayList<>(Arrays.asList(ftpClient.listNames(path)));
//            fileNames.removeIf(fileName -> !fileName.contains(".") && !fileName.contains("_"));
//
//            if(fileNames.isEmpty()) {
//                log.info("{} has {} file", path, 0 );
//                return;
//            }
//
//            // only read the suuitable system -> ignore files
//            SystemType currentSystem = TenantContextHolder.getCurrentSystem();
//            if(currentSystem != null && currentSystem.equals(SystemType.SYSTEM_5GA)) {
//                fileNames.removeIf(fileName -> fileName.toUpperCase().contains("GNODEB_") && fileName.toUpperCase().contains("_LTE_"));
//
//            }else if (currentSystem != null && currentSystem.equals(SystemType.SYSTEM_4GA)) {
//                fileNames.removeIf(fileName -> fileName.toUpperCase().contains("GNODEB_") && fileName.toUpperCase().contains("_NR_"));
//            }
//
//            int numOfReadFileCurrent = totalFileRead.get();
//            totalFileRead.set(totalFileRead.get() + fileNames.size());
//            int countFile = Math.min(totalFileRead.get(), limitMaximumFile);
//            log.info("{} will read {} file(s)", path,countFile - numOfReadFileCurrent);
//            for(int idx = 0;idx < countFile - numOfReadFileCurrent; idx++) {
//                try {
//                    // parsing file data
//                    var preCounter = readFileClickHosue(path, fileNames.get(idx).substring(fileNames.get(idx).lastIndexOf("/") + 1));
//                    cfh.onSuccess(preCounter);
//
//                }catch (Exception e) {
//                    log.error(e.getMessage(), e);
//                }
//            }
//
//
//        }catch (Exception e){
//            log.error(e.getMessage(), e);
//            if(retry < MAX_RETRy) {
//                log.info("{}-{}", retry, "RETRY CONNECT FTP SERVER");
//                reconnect();
//                 getListFileClickHouse(path, limitMaximumFile, totalFileRead, cfh, retry + 1);
//            }
//        }
//
//    }
//
//    private CounterDataObject readFileClickHosue(String path, String fileName) {
////        log.info("Start read file{}", fileName);
//        CounterDataObject preCounter = null;
//        InputStream inputStream = null;
//        try {
//            inputStream = ftpClient.retrieveFileStream(path + "/"  + fileName);
////            log.debug("Done read file from ftp: {}", fileName);
//            if(fileName.toUpperCase().contains("_GNODEB_") && fileName.toUpperCase().contains("RU")) {
//                List<String> lstLine = new ArrayList<>();
//                String line;
//                try(BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
//                    while((line = br.readLine()) != null) {
//                        lstLine.add(line);
//                    }
//                }catch (Exception e){
//                    log.error(e.getMessage(), e);
//                }
//                preCounter = new CounterDataObject(ftpServerObject, path,fileName, null, lstLine);
//            }else {
//                byte[] message = inputStream.readAllBytes();
//                ByteBuffer bufferData =  ByteBuffer.wrap(message);
//                log.debug("Capacity: {}, limit: {}", bufferData.capacity(), bufferData.limit());
//                preCounter = new CounterDataObject(ftpServerObject, path, fileName, bufferData, null);
//            }
//        }catch (Exception e) {
//            log.error(e.getMessage(), e);
//        }finally {
//            try {
//                if(inputStream != null && ftpClient != null && ftpClient.isConnected()) {
//                    inputStream.close();
//                    ftpClient.completePendingCommand();
//                }
//            }catch (Exception e){
//                log.error(e.getMessage(), e);
//            }
//        }
//        return preCounter;
//        }
//
//    public boolean storeFile(String remote, byte[] byteData) {
//        return storeFile(remote, byteData, 0, null);
//    }
//
//    public synchronized boolean storeFile(String remote, InputStream stream) {
//        return storeFile(remote, null, 0, stream);
//    }
//
//    private boolean storeFile(String remote, byte[] byteData, int retry, InputStream stream) {
//        InputStream inputStream = null;
//        if(stream == null) {
//            inputStream = new ByteArrayInputStream(byteData);
//        }else {
//            inputStream = stream;
//        }
//        try {
//            return ftpClient.storeFile(remote, inputStream);
//        }catch (Exception e) {
//            log.error(e.getMessage(), e);
//            if(retry < MAX_RETRy) {
//                reconnect();
//                return storeFile(remote, byteData, retry + 1, stream);
//            }
//            return false;
//        }
//
//    }
//
//    public boolean checkDirectoryExists(String dirPath) {
//        return checkDirectoryExists(dirPath, 3);
//    }
//
//    private boolean checkDirectoryExists(String dirPath, int retry) {
//        try {
//            ftpClient.changeWorkingDirectory(dirPath);
//            int returnCode = ftpClient.getReplyCode();
//            if(returnCode == 550) {
//                return  false;
//            }
//            return true;
//        }catch (IOException e) {
//            log.error(e.getMessage(), e);
//            if(retry < MAX_RETRy) {
//                reconnect();
//                return checkDirectoryExists(dirPath, retry + 1);
//            }
//            return false;
//        }
//    }
//
//    public boolean createDir(String dirPath) {
//        return createDir(dirPath, 3);
//    }
//
//    public boolean createDir(String dirPath, int retry) {
//        try {
//            return ftpClient.makeDirectory(dirPath);
//        }catch (Exception e) {
//            log.error(e.getMessage(), e);
//            if(retry < MAX_RETRy) {
//                reconnect();
//                return createDir(dirPath, retry + 1);
//            }
//            return false;
//        }
//    }
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//}



package com.viettel.ems.perfomance.service;

import com.viettel.ems.perfomance.common.CounterFileHandler;
import com.viettel.ems.perfomance.config.ContextAwareExecutor;
import com.viettel.ems.perfomance.config.SystemType;
import com.viettel.ems.perfomance.config.TenantContextHolder;
import com.viettel.ems.perfomance.object.CounterDataObject;
import com.viettel.ems.perfomance.object.FtpServerObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class FtpServer {
    private final FtpServerObject ftpServerObject;
    private final ContextAwareExecutor contextAwareExecutor;
    private static final int MAX_RETRY = 3;

    public FtpServer(FtpServerObject ftpServerObject, ContextAwareExecutor contextAwareExecutor) {
        this.ftpServerObject = ftpServerObject;
        this.contextAwareExecutor = contextAwareExecutor;
    }

    /**
     * Phương thức tiện ích để tạo và cấu hình một kết nối FTP mới, an toàn.
     * Mỗi luồng sẽ gọi phương thức này để có kết nối của riêng mình.
     */
    private FTPClient createAndConnectClient() throws IOException {
        FTPClient ftpClient = new FTPClient();
        // Cấu hình timeout để tránh luồng bị treo vô hạn
        ftpClient.setConnectTimeout(30000); // 30 giây
        ftpClient.setDefaultTimeout(60000); // 1 phút
        ftpClient.setControlEncoding("UTF-8");
        ftpClient.setAutodetectUTF8(true);

        ftpClient.connect(ftpServerObject.getHost(), ftpServerObject.getPort());
        int reply = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftpClient.disconnect();
            throw new IOException("FTP Server refused connection. Reply code: " + reply);
        }

        if (!ftpClient.login(ftpServerObject.getUsername(), ftpServerObject.getPassword())) {
            ftpClient.logout();
            ftpClient.disconnect();
            throw new IOException("FTP Server login failed for user " + ftpServerObject.getUsername());
        }

        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
        ftpClient.enterLocalPassiveMode(); // Rất quan trọng để hoạt động qua firewall
        ftpClient.setDataTimeout(60000); // 1 phút cho việc truyền dữ liệu

        return ftpClient;
    }

    /**
     * Tải file song song. Luồng chính chỉ liệt kê, các luồng công nhân sẽ tải file.
     */
    public void getListFile(String path, int limitMaximumFile, AtomicInteger totalFileRead, CounterFileHandler cfh) {
        FTPClient listerClient = null;
        try {
            listerClient = createAndConnectClient();
            FTPFile[] ftpFiles = listerClient.listFiles(path);

            if (ftpFiles == null || ftpFiles.length == 0) {
                log.info("{} has 0 file", path);
                return;
            }

            List<FTPFile> files = new ArrayList<>(Arrays.asList(ftpFiles));
            files.removeIf(ftpFile -> !ftpFile.isFile());

            log.info("{} has {} file(s)", path, files.size());

            int currentReadCount = totalFileRead.get();
            if (currentReadCount >= limitMaximumFile) return;

            int filesToProcessCount = Math.min(files.size(), limitMaximumFile - currentReadCount);
            totalFileRead.addAndGet(filesToProcessCount);

            log.info("{} will read {} file(s)", path, filesToProcessCount);

            for (int i = 0; i < filesToProcessCount; i++) {
                final String fileName = files.get(i).getName();

                contextAwareExecutor.execute(() -> {
                    try {
                        CounterDataObject preCounter = readFile(path, fileName);
                        if (preCounter != null) {
                            cfh.onSuccess(preCounter);
                        }
                    } catch (Exception e) {
                        log.error("Error processing file (MySQL flow) {}/{}: {}", path, fileName, e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error listing files (MySQL flow) on FTP path {}: {}", path, e.getMessage());
        } finally {
            if (listerClient != null && listerClient.isConnected()) {
                try {
                    listerClient.logout();
                    listerClient.disconnect();
                } catch (IOException ex) { /* ignore */ }
            }
        }
    }

    /**
     * Tải file song song cho luồng ClickHouse.
     */
    public void getListFileClickHouse(String path, int limitMaximumFile, AtomicInteger totalFileRead, CounterFileHandler cfh) {
        FTPClient listerClient = null;
        try {
            listerClient = createAndConnectClient();
            FTPFile[] ftpFiles = listerClient.listFiles(path);

            if (ftpFiles == null || ftpFiles.length == 0) {
                log.info("{} has 0 file", path);
                return;
            }

            List<FTPFile> files = new ArrayList<>(Arrays.asList(ftpFiles));
            files.removeIf(ftpFile -> !ftpFile.isFile());

            SystemType currentSystem = TenantContextHolder.getCurrentSystem();
            if (currentSystem != null) {
                if (currentSystem.equals(SystemType.SYSTEM_5GA)) {
                    files.removeIf(file -> file.getName().toUpperCase().contains("GNODEB_") && file.getName().toUpperCase().contains("_LTE_"));
                } else if (currentSystem.equals(SystemType.SYSTEM_4GA)) {
                    files.removeIf(file -> file.getName().toUpperCase().contains("GNODEB_") && file.getName().toUpperCase().contains("_NR_"));
                }
            }

            log.info("{} has {} relevant file(s)", path, files.size());

            int currentReadCount = totalFileRead.get();
            if (currentReadCount >= limitMaximumFile) return;

            int filesToProcessCount = Math.min(files.size(), limitMaximumFile - currentReadCount);
            totalFileRead.addAndGet(filesToProcessCount);

            log.info("{} will read {} file(s)", path, filesToProcessCount);

            for (int i = 0; i < filesToProcessCount; i++) {
                final String fileName = files.get(i).getName();

                contextAwareExecutor.execute(() -> {
                    try {
                        CounterDataObject preCounter = readFileClickHosue(path, fileName);
                        if (preCounter != null) {
                            cfh.onSuccess(preCounter);
                        }
                    } catch (Exception e) {
                        log.error("Error processing file (ClickHouse flow) {}/{}: {}", path, fileName, e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error listing files (ClickHouse flow) on FTP path {}: {}", path, e.getMessage());
        } finally {
            if (listerClient != null && listerClient.isConnected()) {
                try {
                    listerClient.logout();
                    listerClient.disconnect();
                } catch (IOException ex) { /* ignore */ }
            }
        }
    }

    /**
     * Đọc một file duy nhất. Tự tạo và đóng kết nối.
     */
    private CounterDataObject readFile(String path, String fileName) throws IOException {
        FTPClient ftpClient = null;
        try {
            ftpClient = createAndConnectClient();
            String fullRemotePath = path.endsWith("/") ? path + fileName : path + "/" + fileName;

            try (InputStream inputStream = ftpClient.retrieveFileStream(fullRemotePath)) {
                if (inputStream == null) {
                    throw new IOException("File not found or stream could not be opened. FTP reply: " + ftpClient.getReplyString());
                }
                // Logic đọc file cũ của bạn
                if(fileName.toUpperCase().contains("_GNODEB_") && fileName.toUpperCase().contains("RU")) {
                    List<String> lstLine = new ArrayList<>();
                    try(BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
                        String line;
                        while((line = br.readLine()) != null) {
                            lstLine.add(line);
                        }
                    }
                    return new CounterDataObject(ftpServerObject, path, fileName, null, lstLine);
                } else {
                    byte[] message = inputStream.readAllBytes();
                    ByteBuffer bufferData = ByteBuffer.wrap(message);
                    return new CounterDataObject(ftpServerObject, path, fileName, bufferData, null);
                }
            } finally {
                if (ftpClient.isConnected() && !ftpClient.completePendingCommand()) {
                    log.warn("Failed to complete pending command for file {}", fileName);
                }
            }
        } finally {
            if (ftpClient != null && ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        }
    }

    /**
     * Đọc một file duy nhất cho luồng ClickHouse. Tự tạo và đóng kết nối.
     */
    private CounterDataObject readFileClickHosue(String path, String fileName) throws IOException {
        FTPClient ftpClient = null;
        try {
            ftpClient = createAndConnectClient();
            String fullRemotePath = path.endsWith("/") ? path + fileName : path + "/" + fileName;

            try (InputStream inputStream = ftpClient.retrieveFileStream(fullRemotePath)) {
                if (inputStream == null) {
                    throw new IOException("File not found or stream could not be opened. FTP reply: " + ftpClient.getReplyString());
                }
                // Logic đọc file cũ của bạn
                if(fileName.toUpperCase().contains("_GNODEB_") && fileName.toUpperCase().contains("RU")) {
                    List<String> lstLine = new ArrayList<>();
                    try(BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
                        String line;
                        while((line = br.readLine()) != null) {
                            lstLine.add(line);
                        }
                    }
                    return new CounterDataObject(ftpServerObject, path, fileName, null, lstLine);
                } else {
                    byte[] message = inputStream.readAllBytes();
                    ByteBuffer bufferData = ByteBuffer.wrap(message);
                    return new CounterDataObject(ftpServerObject, path, fileName, bufferData, null);
                }
            } finally {
                if (ftpClient.isConnected() && !ftpClient.completePendingCommand()) {
                    log.warn("Failed to complete pending command for file {}", fileName);
                }
            }
        } finally {
            if (ftpClient != null && ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        }
    }

    /**
     * Phương thức an toàn để đổi tên/di chuyển file, với cơ chế thử lại.
     */
    public boolean renameFile(String fromPath, String toPath) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            FTPClient ftpClient = null;
            try {
                ftpClient = createAndConnectClient();
                if (ftpClient.rename(fromPath, toPath)) {
                    return true;
                } else {
                    log.warn("Attempt {} to rename from {} to {} failed. FTP reply: {}", attempt, fromPath, toPath, ftpClient.getReplyString());
                }
            } catch (IOException e) {
                log.error("Attempt {} to rename file from {} to {} caught exception: {}", attempt, fromPath, toPath, e.getMessage());
            } finally {
                if (ftpClient != null && ftpClient.isConnected()) {
                    try {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    } catch (IOException e) { /* ignore */ }
                }
            }
        }
        log.error("Failed to rename file from {} to {} after {} attempts.", fromPath, toPath, MAX_RETRY);
        return false;
    }

    /**
     * Lấy một file duy nhất, thường được dùng bởi consumer từ Kafka.
     */
    public CounterDataObject getFile(String path, String fileName) {
        try {
            // Vì tác vụ này thường là đơn lẻ, có thể không cần retry phức tạp
            return readFile(path, fileName);
        } catch (IOException e) {
            log.error("Failed to get single file {}/{}: {}", path, fileName, e.getMessage());
            return null;
        }
    }
}