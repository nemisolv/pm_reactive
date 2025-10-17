//package com.viettel.betterversion2.parser;
//
//import com.viettel.dal.*;
//import com.viettel.entity.Counter;
//import com.viettel.entity.CounterCat;
//import com.viettel.entity.GroupCounterCounter;
//import com.viettel.entity.KpiFormula;
//import com.viettel.util.Constant;
//import com.viettel.util.DataUtil;
//import com.viettel.util.OtherFilterUtil;
//import lombok.extern.slf4j.Slf4j;
//
//import java.text.ParseException;
//import java.util.*;
//import java.util.stream.Collectors;
//
//import static com.viettel.betterversion2.parser.CommonDataBuilder.sdf;
//import static com.viettel.betterversion2.parser.SqlGenerationConfig.*;
//
//@Slf4j
//public class ClickhouseSQLGenerator implements SQLGenerator {
//    @Override
//    public List<QueryObject> build(BuildContext ctx) {
//        List<QueryObject> lstQueryObjects = new ArrayList<>();
//
//        InputStatisticData inputData = ctx.getInputData();
//
//
//        StringBuilder sbSelect = new StringBuilder();
//        StringBuilder sbGroupBy = new StringBuilder();
//        StringBuilder sbOrderBy = new StringBuilder();
//        StringBuilder sbKeyGroupBy = new StringBuilder();
//
//
//        Map<String, Set<Integer>> hmGroupCounter = new HashMap<>();  // for querying counters
//        Map<String, Set<KpiFormula>> hmGroupKPI = new HashMap<>();  // for querying kpis
//
//
//        AllInputKpiCounterObjFlatten allInputKpiCounterObjFlatten = ctx.getAllInputKpiCounterObjFlatten();
//
//        List<KpiFormula> allKpis = allInputKpiCounterObjFlatten.getAllKpiObj();
//        List<Counter> allCounters = allInputKpiCounterObjFlatten.getAllCounterObj();
//        Set<Integer> allKpiCounterId = allInputKpiCounterObjFlatten.getAllKpiCounterId();
//
//
//        List<Integer> allSimpleCounterKpiId = Optional.ofNullable(inputData.getListCounterKPI()).orElse(new ArrayList<>());
//
//        // kpi tu nhieu bang
//        List<KpiFormula> complexKpis = allKpis.stream().filter(kpi ->
//            (kpi.getArrKpi() != null && !kpi.getArrKpi().isEmpty())
//                || (kpi.getArrGroup() != null && kpi.getArrGroup().split(",").length > 1)
//        ).toList();
//
//
//        List<KpiFormula> simpleKpis = allKpis.stream().filter(kpi -> !complexKpis.contains(kpi)).toList();
//
//
//        complexKpis.forEach(kpi -> allSimpleCounterKpiId.removeIf(kpiIdx -> kpi.getKpiID() == kpiIdx));
//
//
//        List<CounterCat> lstAllCounterCat = ctx.getLstAllCounterCat();
//        List<GroupCounterCounter> lstAllGroupCounterCounter = ctx.getLstAllGroupCounterCounter();
//        LinkedHashMap<String, ExtraFieldObject> hmExtraField = ctx.getHmExtraField();
//        HashMap<String, ExtraFieldObject> hmKeyField = ctx.getHmKeyField();
//
//        simpleKpis.forEach(kpiFormula -> {
//            String[] arrGrp = kpiFormula.getArrGroup() != null ? kpiFormula.getArrGroup().split(",") : new String[0];
//            String[] arrRelatedKpi = kpiFormula.getArrKpi() != null ? kpiFormula.getArrKpi().split(",") : new String[0];
//            String[] arrCounter = kpiFormula.getArrCounter() != null ? kpiFormula.getArrCounter().split(",") : new String[0];
//
//            for (var relatedCounter : arrCounter) {
//                allSimpleCounterKpiId.add(Integer.parseInt(relatedCounter.trim()));
//            }
//
//            if (arrGrp.length > 0) {
//                int groupId = Integer.parseInt(arrGrp[0]);
//                lstAllCounterCat.stream()
//                    .filter(e -> groupId == e.getCounterCatID())
//                    .findAny()
//                    .ifPresent(counterCat -> hmGroupKPI.computeIfAbsent(counterCat.getCode(),
//                        (key) -> new HashSet<>(Arrays.asList(kpiFormula))).add(kpiFormula));
//            }
//        });
//
//
//        // map coutner/kpi vao bang tuong ung
//        List<GroupCounterCounter> lstGroupCounterCounters = lstAllGroupCounterCounter.stream()
//            .filter(e -> allSimpleCounterKpiId.contains(e.getCounterID()))
//            .sorted(Comparator.comparing(GroupCounterCounter::getCode))
//            .toList();
//
//        lstGroupCounterCounters.forEach(item -> {
//            hmGroupCounter.computeIfAbsent(item.getCode(), key -> new HashSet<>(List.of(item.getCounterID())))
//                .add(item.getCounterID());
//        });
//
//
//        String listNe = inputData.getListNE().toString();
//        listNe = listNe.substring(1, listNe.length() - 1);
//
//        // TODO: Extra field
//        // 1. sbSelect, sbGroupBy, sbOrderBy
//
//        hmExtraField.forEach((key, value) -> {
//            if (SqlGenerationConfig.Time.DATE_TIME_KEY.equalsIgnoreCase(key)) {
//                // Logic for ClickHouse database
//                sbSelect.append(SPACE_CHAR).append(CommonDataBuilder.getDateRoundClickHouse(inputData.getInterval(),
//                    inputData.getIntervalUnit())).append(COMMA_CHAR);
//                sbOrderBy.append(SPACE_CHAR).append(value.getColumnName()).append(SPACE_CHAR).append("DESC").append(COMMA_CHAR);
//                sbGroupBy.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
//            } else {
//                sbSelect.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
//                sbOrderBy.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
//                sbGroupBy.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
//            }
//        });
//
//        // sbKeyGroupBy
//        hmKeyField.forEach((key, val) -> {
//            sbKeyGroupBy.append(SPACE_CHAR).append(val.getColumnName()).append(COMMA_CHAR);
//        });
//
//        sbSelect.setLength(sbSelect.length() - 1);
//        sbGroupBy.setLength(sbGroupBy.length() - 1);
//        sbOrderBy.setLength(sbOrderBy.length() - 1);
//        sbKeyGroupBy.setLength(sbKeyGroupBy.length() - 1);
//
//        // 2. Filter
//        StringBuilder sbExtraFieldFilter = new StringBuilder();
//        if (inputData.getOtherFilters() != null && !inputData.getOtherFilters().isEmpty()) {
//            String otherFilter = OtherFilterUtil.generateExtraFilter(inputData.getOtherFilters(), hmKeyField);
//            sbExtraFieldFilter.append("AND").append(SPACE_CHAR).append("(").append(otherFilter).append(")");
//        }
//
//        // minute/duration: convert to second
//
//        if (hmGroupCounter.size() >= hmGroupKPI.size()) {
//            String finalListNe = listNe;
//
//            hmGroupCounter.forEach((k, v) -> {
//                QueryObject queryObject = new QueryObject();
//
//                List<Counter> lstGroupCounter = allCounters.stream()
//                    .filter(item -> v.contains(item.getCounterID()))
//                    .collect(Collectors.toList());
//
//                // All Counter
//                StringBuilder sbAllCounter = new StringBuilder();
//                String strAllCounter = "";
//                lstGroupCounter.forEach(c -> {
//                    sbAllCounter.append("AVG(c_").append(c.getCounterID()).append(") AS \"").append("c_")
//                        .append(c.getCounterID()).append("\"").append(COMMA_CHAR).append(SPACE_CHAR);
//                });
//                strAllCounter = sbAllCounter.substring(0, sbAllCounter.length() - 2);
//
//                // KPI/Counter can hiện thi ra output
//                List<String> lstColumnShow = new ArrayList<>();
//                Set<String> column = new HashSet<>();
//
//                if (!inputData.isShowRecentCounter()) {
//                    lstGroupCounter.removeIf(e -> !allKpiCounterId.contains(e.getCounterID()));
//                }
//
//                lstGroupCounter.forEach(c -> {
//                    lstColumnShow.add("round(" + c.getFormula() + ",2) AS \"" +
//                        (inputData.isReturnCounterKpiId() ? c.getCounterID() : c.getName()) +
//                        "\" ");
//                    column.add(inputData.isReturnCounterKpiId() ? String.valueOf(c.getCounterID()) : c.getName());
//                });
//
//                Set<KpiFormula> sGroupKpiFormulas = hmGroupKPI.get(k);
//                if (sGroupKpiFormulas != null) {
//                    sGroupKpiFormulas.forEach(item -> {
//                        String formula = item.getFormula();
//                        lstColumnShow.add(
//                            "round(" + formula + ",2) AS \"" +
//                                (inputData.isReturnCounterKpiId() ? item.getKpiID()
//                                    : (item.getKpiName() + " (" + item.getUnits() + ")"))
//                                +
//                                "\" ");
//                        column.add(inputData.isReturnCounterKpiId() ? String.valueOf(item.getKpiID())
//                            : (item.getKpiName() + " (" + item.getUnits() + ")"));
//                    });
//                }
//
//                String strSQLOutputCounterKpi = lstColumnShow.toString();
//                strSQLOutputCounterKpi = strSQLOutputCounterKpi.substring(1, strSQLOutputCounterKpi.length() - 1);
//
//                // Hour range generate
//                StringBuilder timeRangeSQL = new StringBuilder();
//                if (inputData.getRanges() != null) {
//                    timeRangeSQL = CommonDataBuilder.getStringBuilderTimeRange(inputData);
//                }
//
//                // Build SQL
//                try {
//                    String sqlQuery = buildSql(
//                        inputData,
//                        finalListNe,
//                        sbExtraFieldFilter.toString(),
//                        strAllCounter,
//                        strSQLOutputCounterKpi,
//                        k,
//                        timeRangeSQL.toString(),
//                        sbSelect.toString(),
//                        sbGroupBy.toString(),
//                        sbOrderBy.toString(),
//                        sbKeyGroupBy.toString());
//
//                    queryObject.setSql(sqlQuery);
//                    queryObject.setColumn(column);
//
//                    lstQueryObjects.add(queryObject);
//
//                } catch (ParseException e) {
//                    log.error("ParseException( maybe due to date): {}", e.getMessage());
//
//                }
//            });
//        } else {
//            log.error("Number of Group not equal.");
//        }
//
//        boolean isExistedKpiFromMultiTbl = !complexKpis.isEmpty();
//
//        if (isExistedKpiFromMultiTbl) {
//            handleKpiMutiTable(complexKpis, allKpis, allCounters, lstQueryObjects,
//                lstAllGroupCounterCounter, inputData, hmExtraField, hmKeyField);
//        }
//
//        return lstQueryObjects;
//
//    }
//
//    @Override
//    public SqlDialect dialect() {
//        return SqlDialect.CLICKHOUSE;
//    }
//
//
//
//    private void handleKpiMutiTable(List<KpiFormula> complexKpis, List<KpiFormula> allKpis, List<Counter> allCounters,
//                                    List<QueryObject> lstQueryObjects, List<GroupCounterCounter> lstAllGroupCounterCounter,
//                                    InputStatisticData inputData, LinkedHashMap<String, ExtraFieldObject> hmExtraField,
//                                    Map<String, ExtraFieldObject> hmKeyField) {
//    }
//
//
//
//    private String buildSql(InputStatisticData inputData, String neList, String sExtraFieldFilter,
//                            String strAllCounter, String columnShow,String tableName,
//                            String timeRange,
//                        String sSelect,
//                            String sGroupBy,
//                            String sOrderBy,
//                            String sKeyGroupBy) throws ParseException {
//        String sDuration = CommonDataBuilder.calculateDuration(inputData);
//        final String fromTime = inputData.getFromTime();
//        final String toTime = CommonDataBuilder.getToTime(inputData.getToTime());
//        final List<String> ratTypeList = inputData.getRatTypeList();
//
//        Date truncToTime = CommonDataBuilder.getRoundDate(sdf.parse(toTime), inputData
//            .getInterval(), inputData.getIntervalUnit());
//        StringBuilder sql = new StringBuilder("SELECT");
//        sql.append(sSelect);
//        sql.append(COMMA_CHAR).append(SPACE_CHAR).append(columnShow).append(DOWN_LINE_CHAR);
//        sql.append("FROM (");
//        sql.append("SELECT record_time, ne_id");
//        sql.append(COMMA_CHAR).append(SPACE_CHAR).append(sKeyGroupBy);
//        sql.append(COMMA_CHAR).append(SPACE_CHAR).append(strAllCounter).append(DOWN_LINE_CHAR);
//
//        sql.append("FROM ").append(tableName).append(DOWN_LINE_CHAR);
//
//        sql.append("WHERE record_time >= '").append(fromTime).append("' AND record_time < '").append(sdf.format(truncToTime));
//        sql.append("' ").append(timeRange).append(sDuration).append(" AND ne_id IN (").append(neList).append(")");
//
//        //add rat_type for mix-mode
//        if (DataUtil.isNotNullNotEmpty(ratTypeList)) {
//            List<String> ratTypeListWithQuote = ratTypeList.stream().map(ratType -> "'" + ratType + "'").collect(Collectors.toList());
//            String ratTypeStr = String.join(",", ratTypeListWithQuote);
//            sql.append(" AND rat_type IN ( ").append(ratTypeStr).append(" )");
//        }
//
//        sql.append(SPACE_CHAR).append(sExtraFieldFilter).append(DOWN_LINE_CHAR);
//        sql.append("GROUP BY ").append(sKeyGroupBy).append(DOWN_LINE_CHAR);
//        sql.append(")").append(DOWN_LINE_CHAR);
//        sql.append("GROUP BY").append(sGroupBy).append(DOWN_LINE_CHAR);
//        sql.append("ORDER BY ").append(sOrderBy).append(DOWN_LINE_CHAR);
//        return sql.toString();
//    }
//}
