package com.viettel.util;

public class Constant {

    public static final String DATE_TIME_KEY = "date_time"; // name in api request/repsonse
    public static final String DATE_TIME_VALUE = "report_time"; // name display for report
    public static final String DATE_TIME_VALUE_CLICKHOUSE = "record_time"; 
    public static final String DATE_TIME_DISPLAY = "Datetime"; // name display for report
    public static final String DATE_KEY = "date";
    public static final String NE_KEY = "ne_name";
    public static final String NE_VALUE = "ne_id";
    public static final String NE_DISPLAY = "NE Name";
    public static final String LOG_FORMAT = "{}";
    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    public enum IntervalUnit {
    SECOND(1), MINUTE(2), HOUR(3), DATE(4), WEEK(5), MONTH(6);

    private final int value;

    IntervalUnit(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }






}

    public  enum DBType {
        MYSQL("mysql"), CLICKHOUSE("clickhouse");
        private final String value;
        DBType(String value) {
            this.value = value;
        }
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

}