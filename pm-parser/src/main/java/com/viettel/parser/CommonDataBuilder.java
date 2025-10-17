//package com.viettel.betterversion2.parser;
//
//import com.viettel.dal.InputStatisticData;
//import com.viettel.dal.TimeRange;
//import com.viettel.util.Constant;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.time.DateUtils;
//
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
//import java.util.Calendar;
//import java.util.Date;
//import java.util.List;
//import java.util.Map;
//
//import static com.viettel.betterversion2.parser.SqlGenerationConfig.COMMA_CHAR;
//import static com.viettel.betterversion2.parser.SqlGenerationConfig.DOWN_LINE_CHAR;
//
//@Slf4j
//public class CommonDataBuilder {
//    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//
//    private static final Map<Integer, Integer> DURATION_MULTIPLIERS = Map.of(
//        Constant.IntervalUnit.MINUTE.getValue(), 60,
//        Constant.IntervalUnit.SECOND.getValue(), 1,
//        Constant.IntervalUnit.HOUR.getValue(), 3600,
//        Constant.IntervalUnit.DATE.getValue(), 86400
//    );
//
//
//    public static boolean isGTnow(String date) {
//        try {
//            return Calendar.getInstance().getTime().getTime() > sdf.parse(date).getTime();
//        } catch (ParseException e) {
//            log.error("ParseException: {}", e.getMessage());
//            return false;
//        }
//    }
//
//    public static String getToTime(String toTime) {
//        return  isGTnow(toTime)
//            ? toTime
//            : sdf.format(Calendar.getInstance().getTime());
//    }
//
//    public static String calculateDuration(InputStatisticData inputStatisticData) {
//        if (inputStatisticData.getDuration() <= 0) {
//            return "";
//        }
//
//        Integer multiplier = DURATION_MULTIPLIERS.get(inputStatisticData.getIntervalUnit());
//        if (multiplier == null) {
//            return "";
//        }
//
//        return " AND duration = " + (inputStatisticData.getDuration() * multiplier);
//    }
//
//
//
//     public static StringBuilder getStringBuilderTimeRange(InputStatisticData inputStatisticData) {
//        StringBuilder timeRangeSQL = new StringBuilder();
//        List<TimeRange> ranges = inputStatisticData.getRanges();
//        if (ranges != null && !ranges.isEmpty()) {
//            timeRangeSQL.append("AND (");
//            for (int idx = 0; idx < ranges.size(); idx++) {
//                TimeRange timeRange = ranges.get(idx);
//                long hourStart = Long.parseLong(timeRange.getStart().split(":")[0]) * 60 * 60 * 1000;
//                long minStart = Long.parseLong(timeRange.getStart().split(":")[1]) * 60 * 1000;
//                long hourEnd = Long.parseLong(timeRange.getEnd().split(":")[0]) * 60 * 60 * 1000;
//                long minEnd = Long.parseLong(timeRange.getEnd().split(":")[1]) * 60 * 1000;
//
//                if (idx != 0) {
//                    timeRangeSQL.append("OR ");
//                }
//
//                timeRangeSQL.append(
//                        "(EXTRACT(hour FROM record_time)*60*60*1000 + EXTRACT(minute FROM record_time)*60*1000 + " +
//                            "EXTRACT(second FROM record_time)*1000 >= ")
//                    .append(hourStart + minStart)
//                    .append(" AND ")
//                    .append("(EXTRACT(hour FROM record_time)*60*60*1000 + EXTRACT(minute FROM record_time)*60*1000 + "
//                        +
//                        "EXTRACT(second FROM record_time)*1000 < ")
//                    .append(hourEnd + minEnd)
//                    .append(")");
//            }
//            timeRangeSQL.append(")");
//        }
//        return timeRangeSQL;
//    }
//
//
//    public static Date getRoundDate(Date oriDate, int interval, Integer intervalUnit) {
//        Calendar calendar = Calendar.getInstance();
//        calendar.setTime(oriDate);
//        Date newDate;
//
//        if (intervalUnit.equals(Constant.IntervalUnit.SECOND.getValue())) {
//            newDate = new Date(DateUtils.truncate(calendar.getTime(), Calendar.MINUTE).getTime() +
//                (interval * (calendar.get(Calendar.SECOND) / interval * 1000)));
//        } else if (intervalUnit.equals(Constant.IntervalUnit.MINUTE.getValue())) {
//            if (interval < 60) {
//                newDate = new Date(DateUtils.truncate(calendar.getTime(), Calendar.HOUR).getTime() +
//                    (interval * (calendar.get(Calendar.MINUTE) / interval * 60 * 1000)));
//            } else if (interval < 1440) {
//                newDate = DateUtils.truncate(calendar.getTime(), Calendar.HOUR);
//            } else {
//                newDate = DateUtils.truncate(calendar.getTime(), Calendar.DATE);
//            }
//        } else if (intervalUnit.equals(Constant.IntervalUnit.HOUR.getValue())) {
//            newDate = DateUtils.truncate(calendar.getTime(), Calendar.HOUR_OF_DAY);
//        } else {
//            newDate = DateUtils.truncate(calendar.getTime(), Calendar.DATE);
//        }
//
//        return newDate;
//    }
//
//
//    public static String getDateRoundMySQL(int interval, Integer intervalUnit) {
//        if (intervalUnit.equals(Constant.IntervalUnit.MINUTE.getValue())) {
//            if (interval == 5 || interval == 15 || interval == 30 || interval == 60) {
//                return "DATE_ADD(DATE_FORMAT(record_time,'%Y-%m-%d %H:00:00'), INTERVAL FLOOR(minute(record_time)/("
//                    + interval + ")) *" + interval + " minute) AS report_time";
//            } else if (interval == 1440) {
//                return "DATE_FORMAT(record_time,'%Y-%m-%d 00:00:00') AS report_time";
//            }
//        } else if (intervalUnit.equals(Constant.IntervalUnit.SECOND.getValue())) {
//            return "DATE_ADD(DATE_FORMAT(record_time,'%Y-%m-%d %H:%i:00'), INTERVAL FLOOR(second(record_time)/("
//                + interval + ")) *" + interval + " second) AS report_time";
//        } else if (intervalUnit.equals(Constant.IntervalUnit.HOUR.getValue())) {
//            return "DATE_ADD(DATE_FORMAT(record_time,'%Y-%m-%d 00:00:00'), INTERVAL FLOOR(hour(record_time)/("
//                + interval + ")) *" + interval + " hour) AS report_time";
//        } else if (intervalUnit.equals(Constant.IntervalUnit.DATE.getValue())) {
//            return "DATE_FORMAT(record_time,'%Y-%m-%d 00:00:00') AS report_time";
//        } else if (intervalUnit.equals(Constant.IntervalUnit.WEEK.getValue())) {
//            return "DATE_ADD(DATE_FORMAT(record_time,'%Y-%m-01 00:00:00'), INTERVAL FLOOR(week(record_time)/("
//                + interval + ")) *" + interval + " week) AS report_time";
//        } else if (intervalUnit.equals(Constant.IntervalUnit.MONTH.getValue())) {
//            return "DATE_ADD(DATE_FORMAT(record_time,'%Y-01-00 00:00:00'), INTERVAL FLOOR(month(record_time)/("
//                + interval + ")) *" + interval + " month) AS report_time";
//        }
//
//        // return "record_time AS report_time";
//        return "report_time";
//    }
//
//
//
//
//
//    private static StringBuilder getDateRoundClickHouseCommon(int interval, Integer intervalUnit) {
//        StringBuilder recordTimeString = new StringBuilder("formatDateTime(");
//        if(intervalUnit.equals(Constant.IntervalUnit.MINUTE.getValue())) {
//            if (interval == 5 || interval == 15 || interval == 30 || interval == 60)  {
//                recordTimeString.append("toDateTime(toStartOfInterval(").append("record_time, INTERVAL").append(interval).append(" minute");
//
//            }else if(interval == 1440) {
//                recordTimeString.append("toDateTime(toStartOfInterval(").append("record_time, INTERVAL 1 DAY ))");
//            }
//        }else if(intervalUnit.equals(Constant.IntervalUnit.SECOND.getValue())) {
//            recordTimeString.append("toDateTime(toStartOfInterval(").append("record_time, INTERVAL").append(interval).append(" SECOND))");
//        }else if(intervalUnit.equals(Constant.IntervalUnit.HOUR.getValue())) {
//            recordTimeString.append("toDateTime(toStartOfInterval(").append("record_time, INTERVAL").append(interval).append(" HOUR))");
//        }else if(intervalUnit.equals(Constant.IntervalUnit.DATE.getValue())) {
//            recordTimeString.append("toDateTime(toStartOfInterval(").append("record_time, INTERVAL").append(interval).append(" DAY))");
//        }else if(intervalUnit.equals(Constant.IntervalUnit.WEEK.getValue())) {
//            recordTimeString.append("toDateTime(toStartOfInterval(").append("record_time, INTERVAL").append(interval).append(" WEEK))");
//        }else if(intervalUnit.equals(Constant.IntervalUnit.MONTH.getValue())) {
//            recordTimeString.append("toDateTime(toStartOfInterval(").append("record_time, INTERVAL").append(interval).append(" MONTH))");
//        }else {
//            recordTimeString.append("record_time");
//        }
//        return recordTimeString;  // khong thieu dau ngaoc dong ")", boi vi no se dung build tiep voi 2 ham dung no
//    }
//
//
//
//    public static String getDateRoundClickHouse(int interval, Integer intervalUnit) {
//        return getDateRoundClickHouseCommon(interval, intervalUnit).append(", '%Y-%m-%d %H:%M:%S') AS record_time").toString();
//    }
//
//    public static String getDateRoundClickHouseForMultiTbl(int interval, Integer intervalUnit, List<String> tblAliases) {
//        StringBuilder recordTimeString = getDateRoundClickHouseCommon(interval, intervalUnit);
//        // replace with coalesce
//        StringBuilder nonNullColumnSb = new StringBuilder("coalesce(");
//        for(int i = 0;i < tblAliases.size();i++) {
//            String alias = tblAliases.get(i);
//            nonNullColumnSb.append("nullIf(").append(alias).append(".record_time, toDateTime(0))");
//            if(i < tblAliases.size() - 1) {
//                nonNullColumnSb.append(COMMA_CHAR).append(DOWN_LINE_CHAR);
//            }else {
//                nonNullColumnSb.append(")").append(DOWN_LINE_CHAR);
//            }
//        }
//        String recordTime = recordTimeString.toString().replace("record_time", nonNullColumnSb.toString());
//        return recordTime + ", '%Y-%m-%d %H:%M:%S') AS record_time";
//    }
//
//
//
//
//
//}
