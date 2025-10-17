package com.viettel.ems.perfomance.tools;

import org.capnproto.MessageBuilder;
import org.capnproto.Serialize;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.WritableByteChannel;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

public class CapnprotoMockGenerator {

    private static final int DEFAULT_FILE_COUNT = 5000;
    private static final int DEFAULT_DURATION_SECONDS = 900;
    private static final String DEFAULT_FILENAME_PREFIX = "gNodeB_gNodeB0284";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault());
    private static final String FTP_PATH = "/4ga-uploads";

    public static void main(String[] args) throws Exception {
        String outputDirectoryPath = args != null && args.length > 0 ? args[0] : "build/mock-capnp";
        int fileCount = args != null && args.length > 1 ? parsePositiveInt(args[1], DEFAULT_FILE_COUNT) : DEFAULT_FILE_COUNT;
        String unit = args != null && args.length > 2 ? args[2] : "gNodeB0284"; // default unit

        File outputDirectory = new File(outputDirectoryPath);
        if (!outputDirectory.exists()) {
            if (!outputDirectory.mkdirs()) {
                throw new IllegalStateException("Cannot create output directory: " + outputDirectory.getAbsolutePath());
            }
        }

        Random random = new Random(System.currentTimeMillis());

        for (int i = 1; i <= fileCount; i++) {
            long nowMillis = System.currentTimeMillis();
            String timestamp = TIME_FORMATTER.format(Instant.ofEpochMilli(nowMillis));

//            String stationName = String.format("%s%02d", DEFAULT_FILENAME_PREFIX, i);
            String stationName =  DEFAULT_FILENAME_PREFIX;
            long cellId = random.nextInt(1,3); // cell index (0,3)
            String cellName = stationName + random.nextInt(1,3);
            String nodeFunction = pickNodeFunction(i);

            String location = String.format("ManagedElement=%s,NodeFunction=%s,CellName=%s,CellId=%d",
                    stationName, nodeFunction, cellName, cellId);

            MessageBuilder message = new MessageBuilder();

            CounterSchema.CounterDataCollection.Builder root =
                    message.initRoot(CounterSchema.CounterDataCollection.factory);

            var dataList = root.initData(1);
            CounterSchema.CounterData.Builder counterData = dataList.get(0);
            counterData.setTime(nowMillis);
            counterData.setDuration(DEFAULT_DURATION_SECONDS);
            counterData.setLocation(location);
            counterData.setCell(cellId);
            counterData.setService(1);
            counterData.setTac(1);
            counterData.setCode(0);
            counterData.setCondition(0);

            // Set unit and type on collection
            root.setUnit(unit);
            root.setType(0);

            // Create some counter values with ids 68 and/or 69
            int numValues = 2 + random.nextInt(3); // 2..4 values
            var values = counterData.initData(numValues);
            for (int v = 0; v < numValues; v++) {
                CounterSchema.CounterValue.Builder val = values.get(v);
                int id = (v % 2 == 0) ? 68 : 69;
                long value = 10 + random.nextInt(10_000);
                val.setId(id);
                val.setValue(value);
            }

            String fileName = String.format("%s_%s.capnproto", stationName, timestamp);
            File outFile = new File(outputDirectory, fileName);

            try (FileOutputStream fos = new FileOutputStream(outFile);
                 WritableByteChannel channel = fos.getChannel()) {
                Serialize.write(channel, message);
            }

            System.out.printf("Generated: %s (%s)\n", outFile.getAbsolutePath(), location);
        }
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String pickNodeFunction(int index) {
        // Allowed values: NR_TDD, NR_FDD, LTE_TDD, LTE_FDD, 1
        return switch (index % 5) {
            case 1 -> "NR_TDD";
            case 2 -> "NR_FDD";
            case 3 -> "LTE_TDD";
            case 4 -> "LTE_FDD";
            default -> "1";
        };
    }
}


