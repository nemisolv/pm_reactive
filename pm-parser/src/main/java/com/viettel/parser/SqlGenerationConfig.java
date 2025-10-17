//package com.viettel.betterversion2.parser;
//
//import com.viettel.util.Constant;
//
//import java.util.Map;
//
///**
// * Centralized configuration for SQL generation constants.
// */
//public final class SqlGenerationConfig {
//    public static final String SPACE_CHAR = " ";
//    public static final String COMMA_CHAR = ",";
//    public static final String DOWN_LINE_CHAR = "\n";
//    public static final String SEMI_COLON_CHAR = ";";
//
//    // Time-related constants
//    public static final class Time {
//        public static final Map<Constant.IntervalUnit, Integer> DURATION_MULTIPLIERS = Map.of(
//            Constant.IntervalUnit.MINUTE, 60,
//            Constant.IntervalUnit.SECOND, 1,
//            Constant.IntervalUnit.HOUR, 3600,
//            Constant.IntervalUnit.DATE, 86400
//        );
//
//        public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
//        public static final String DATE_TIME_KEY = "DATE_TIME_KEY";
//    }
//
//    // Query generation constants
//    public static final class Query {
//        public static final String JOIN_KEY_DELIMITER = "_";
//        public static final String NULL_PLACEHOLDER = "-";
//    }
//
//    // Table aliases
//    public static final class Aliases {
//        public static final String BASE_TABLE_PREFIX = "base";
//        public static final String JOINED_KEY_COLUMN = "joined_key";
//    }
//
//    private SqlGenerationConfig() {
//        // Utility class
//    }
//}
