package com.viettel.util;

import com.viettel.dal.*;
import org.springframework.stereotype.Component;

import com.viettel.repository.CounterKpiRepository;
import com.viettel.repository.GroupCounterCounterRepository;
import com.viettel.repository.KpiCachingRepository;
import com.viettel.util.Constant.IntervalUnit;
import com.viettel.repository.CommonRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SQLParser {
    private CounterKpiRepository counterKpiRepository;
    private GroupCounterCounterRepository groupCounterCounterRepository;
    private Util util;
    private KpiCachingRepository kpiCachingRepository;
    private CommonRepository commonRepository;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final char SPACE_CHAR = ' ';
    private static final char COMMA_CHAR = ',';
    private static final char SEMI_COLON_CHAR = ';';
    private static final char DOWN_LINE_CHAR = '\n';
    private static final String DATE_TIME_KEY = "DATE_TIME_KEY";

    private static final Map<Integer, Integer> DURATION_MULTIPLIERS = Map.of(
    IntervalUnit.MINUTE.getValue(), 60,
    IntervalUnit.SECOND.getValue(), 1,
    IntervalUnit.HOUR.getValue(), 3600,
    IntervalUnit.DATE.getValue(), 86400
);

    private static final String PREFIX_JOIN_BASE_TABLE = "base";

    public SQLParser(CounterKpiRepository counterKpiRepository,
            GroupCounterCounterRepository groupCounterCounterRepository,
            Util util, KpiCachingRepository kpiCachingRepository, CommonRepository commonRepository) {
        this.counterKpiRepository = counterKpiRepository;
        this.groupCounterCounterRepository = groupCounterCounterRepository;
        this.util = util;
        this.kpiCachingRepository = kpiCachingRepository;
        this.commonRepository = commonRepository;
    }

    public CustomOutput<QueryObject> generateSQLCaching(
            InputStatisticData inputData,
            List<Counter> lstAllCounter,
            List<KpiFormula> lstAllKpi,
            List<CounterCat> lstAllCounterCat,
            List<GroupCounterCounter> lstAllGroupCounterCounter,
            LinkedHashMap<String, ExtraFieldObject> hmExtraField,
            HashMap<String, ExtraFieldObject> hmKeyField,
            String dbType) {
        return null; // not support
    }

    private boolean isGTnow(String date) {
        try {
            return Calendar.getInstance().getTime().getTime() > sdf.parse(date).getTime();
        } catch (ParseException e) {
            log.error("ParseException: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unused")
    public CustomOutput<List<QueryObject>> generateSQL(
            InputStatisticData inputData,
            List<Counter> lstAllCounter,
            List<KpiFormula> lstAllKpi,
            List<CounterCat> lstAllCounterCat,
            List<GroupCounterCounter> lstAllGroupCounterCounter,
            LinkedHashMap<String, ExtraFieldObject> hmExtraField,
            HashMap<String, ExtraFieldObject> hmKeyField,
            String dbType) throws ParseException {
        InputStatisticData inputStatisticData = inputData.clone();
        String suffix = "";
        final String fromTime = inputStatisticData.getFromTime();
        final String toTime = isGTnow(inputStatisticData.getToTime())
                ? inputStatisticData.getToTime()
                : sdf.format(Calendar.getInstance().getTime());
        List<QueryObject> lstQueryObjects = new ArrayList<>();
        List<Integer> lstInputCounterKpiID = new ArrayList<>();
        StringBuilder sbSelect = new StringBuilder();
        StringBuilder sbGroupBy = new StringBuilder();
        StringBuilder sbOrderBy = new StringBuilder();
        StringBuilder sbKeyGroupBy = new StringBuilder();

        Map<String, Set<Integer>> hmGroupCounter = new HashMap<>();
        Map<String, Set<KpiFormula>> hmGroupKPI = new HashMap<>();
        List<KpiFormula> lstKpiFormulaWithMultiTable = new ArrayList<>();

        if (inputStatisticData.getListCounterKPI() != null) {
            lstInputCounterKpiID.addAll(inputStatisticData.getListCounterKPI());
        }
        List<Integer> lstAllCounterKpiID = inputStatisticData.getListCounterKPI() != null
                ? inputStatisticData.getListCounterKPI()
                : new ArrayList<>();

        List<KpiFormula> lstKpiFormulas = lstAllKpi.stream()
                .filter(kpi -> lstInputCounterKpiID.contains(kpi.getKpiID())).toList();





        List<Integer> ignoreKpiIds = new ArrayList<>();


        lstKpiFormulas.forEach(item -> {
            String[] arrGrp = item.getArrGroup() != null ? item.getArrGroup().split(",") : new String[0];
            String[] arrRelatedKpi = item.getArrKpi() != null ? item.getArrKpi().split(",") : new String[0];
            if (arrGrp.length <= 1 && arrRelatedKpi.length == 0) {
                // Process arrCounter
                if (item.getArrCounter() != null && !item.getArrCounter().trim().isEmpty()) {
                    String[] arrCounter = item.getArrCounter().split(",");
                    for (String cID : arrCounter) {
                        lstAllCounterKpiID.add(Integer.parseInt(cID.trim()));
                    }
                }

                if (item.getArrKpi() != null && !item.getArrKpi().isEmpty()) {
                    String[] arrKpiID = item.getArrKpi().split(",");
                    if (arrKpiID.length > 1) {
                        for (String kpiId : arrKpiID) {
                            lstAllCounterKpiID.add(Integer.parseInt(kpiId.trim()));
                        }
                    }
                    lstAllCounterKpiID.remove(Integer.valueOf(item.getKpiID()));
                }
            }
//            else {
                lstKpiFormulaWithMultiTable.add(item);
//                ignoreKpiIds.add(item.getKpiID());
//            }
        });

//        lstAllCounterKpiID.removeAll(ignoreKpiIds);

        // truoc khi tinh kpi don le, ta can check neu no da ton tai trong kpi phuc tap( multiple table) thi bo no di
        Set<Integer> childKpiIds = new HashSet<>();
        Set<Integer> flattenAllInputKpiCounters = new HashSet<>();
        for(var kpi: lstKpiFormulaWithMultiTable) {
            collectAllKpiAndCounters(kpi, flattenAllInputKpiCounters, new HashSet<>(), lstAllKpi);
        }

// Bước 2: Phát hiện KPI con (KPI được sử dụng bởi KPI khác)
        for(var kpi: lstKpiFormulaWithMultiTable) {
            findChildKpis(kpi, lstAllKpi, childKpiIds, new HashSet<>());
        }
//        lstKpiFormulas.forEach(kpi -> {
//            if(flattenAllInputKpiCounters.contains(kpi.getKpiID())) {
//                if(kpi.getArrKpi() == null || kpi.getArrKpi().isEmpty()) {
//                    childKpiIds.add(kpi); // kpi goc
//                }
//            }
//        });

        lstAllCounterKpiID.removeAll(flattenAllInputKpiCounters);
//        lstAllCounterKpiID.removeAll(childKpiIds);
//        List<KpiFormula> finalLstKpiFormulaWithMultiTable =
//
//                lstKpiFormulaWithMultiTable.stream().filter(kpiFormula -> !childKpiIds.contains(kpiFormula.getKpiID())).toList();

        List<KpiFormula> list = lstKpiFormulas.stream().filter(item -> childKpiIds.contains(item.getKpiID())).toList();
        List<KpiFormula> finalLstKpiFormulaWithMultiTable = lstKpiFormulaWithMultiTable.stream()
                .filter(kpiFormula -> !list.contains(kpiFormula)).toList();


        List<GroupCounterCounter> lstGroupCounterCounters = lstAllGroupCounterCounter.stream()
                .filter(e -> lstAllCounterKpiID.contains(e.getCounterID()))
                .sorted(Comparator.comparing(GroupCounterCounter::getCode))
                .toList();

        lstGroupCounterCounters.forEach(item -> {
            hmGroupCounter.computeIfAbsent(item.getCode(), key -> new HashSet<>(List.of(item.getCounterID())))
                    .add(item.getCounterID());
        });

        lstKpiFormulas.forEach(kpiFormula -> {
            String[] arrGroup = kpiFormula.getArrGroup() != null ? kpiFormula.getArrGroup().split(",") : new String[0];
            String[] arrRelatedKpi = kpiFormula.getArrKpi() != null ? kpiFormula.getArrKpi().split(",") : new String[0];

//            if ((arrGroup.length > 1 || arrRelatedKpi.length >= 1) && "clickhouse".equalsIgnoreCase(dbType)) {
//                if (arrRelatedKpi.length >= 1) {
//                    log.info("Detected KPI with nested dependencies: {} (ID: {})", kpiFormula.getKpiName(),
//                            kpiFormula.getKpiID());
//                }
//                lstKpiFormulaWithMultiTable.add(kpiFormula);
//            } else {
                // Single group KPI
                if (arrGroup.length > 0 && !flattenAllInputKpiCounters.contains(kpiFormula.getKpiID())) {
                    int groupId = Integer.parseInt(arrGroup[0]);
                    lstAllCounterCat.stream()
                            .filter(e -> groupId == e.getCounterCatID())
                            .findAny()
                            .ifPresent(counterCat -> {
                                hmGroupKPI.computeIfAbsent(counterCat.getCode(), key -> new HashSet<>())
                                        .add(kpiFormula);
                            });
//                }
            }
        });

        String listNe = inputStatisticData.getListNE().toString();
        listNe = listNe.substring(1, listNe.length() - 1);

        // TODO: Extra field
        // 1. sbSelect, sbGroupBy, sbOrderBy

        hmExtraField.forEach((key, value) -> {
            if ("DATE_TIME_KEY".equalsIgnoreCase(key)) {
                if (Constant.DBType.CLICKHOUSE.getValue().equalsIgnoreCase(dbType)) {
                    // Logic for ClickHouse database
                    sbSelect.append(SPACE_CHAR)
                            .append(getDateRoundClickHouse(false, inputStatisticData.getIntervalUnit(),
                                    inputStatisticData.getIntervalUnit(), ""))
                            .append(COMMA_CHAR);
                    sbOrderBy.append(SPACE_CHAR).append(value.getColumnName()).append(SPACE_CHAR).append("DESC")
                            .append(COMMA_CHAR);
                    sbGroupBy.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
                } else {
                    // Logic for other databases
                    sbSelect.append(SPACE_CHAR)
                            .append(getDateRoundMySQL(inputStatisticData.getIntervalUnit(),
                                    inputStatisticData.getIntervalUnit()))
                            .append(COMMA_CHAR);
                    sbOrderBy.append(SPACE_CHAR).append("report_time").append(SPACE_CHAR).append("DESC")
                            .append(COMMA_CHAR);
                    sbGroupBy.append(SPACE_CHAR).append("report_time").append(COMMA_CHAR);
                }
            } else {
                sbSelect.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
                sbOrderBy.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
                sbGroupBy.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
            }
        });

        // sbKeyGroupBy
        hmKeyField.forEach((key, val) -> {
            sbKeyGroupBy.append(SPACE_CHAR).append(val.getColumnName()).append(COMMA_CHAR);
        });

        sbSelect.setLength(sbSelect.length() - 1);
        sbGroupBy.setLength(sbGroupBy.length() - 1);
        sbOrderBy.setLength(sbOrderBy.length() - 1);
        sbKeyGroupBy.setLength(sbKeyGroupBy.length() - 1);

        // 2. Filter

        StringBuilder sbExtraFieldFilter = new StringBuilder();
        if (inputStatisticData.getOtherFilters() != null && !inputStatisticData.getOtherFilters().isEmpty()) {
            String otherFilter = OtherFilterUtil.generateExtraFilter(inputStatisticData.getOtherFilters(), hmKeyField);
            sbExtraFieldFilter.append("AND").append(SPACE_CHAR).append("(").append(otherFilter).append(")");
        }

        // minute/duration: convert to second
        String sDuration = "";
        if (inputStatisticData.getDuration() > 0) {
            sDuration += " AND duration = ";
            if (inputStatisticData.getIntervalUnit().equals(IntervalUnit.MINUTE.getValue())) {
                sDuration += inputStatisticData.getDuration() * 60;
            } else if (inputStatisticData.getIntervalUnit().equals(IntervalUnit.SECOND.getValue())) {
                sDuration += inputStatisticData.getDuration();
            } else if (inputStatisticData.getIntervalUnit().equals(IntervalUnit.HOUR.getValue())) {
                sDuration += inputStatisticData.getDuration() * 60 * 60;
            } else if (inputStatisticData.getIntervalUnit().equals(IntervalUnit.DATE.getValue())) {
                sDuration += inputStatisticData.getDuration() * 60 * 60 * 24;
            }
        }


        if (hmGroupCounter.size() >= hmGroupKPI.size()) {
            String finalListNe = listNe;
            String finalPrefix = suffix;
            String finalSDuration = sDuration;
            hmGroupCounter.forEach((k, v) -> {
                QueryObject queryObject = new QueryObject();

                List<Counter> lstGroupCounter = lstAllCounter.stream()
                        .filter(item -> v.contains(item.getCounterID()))
                        .collect(Collectors.toList());

                // All Counter
                StringBuilder sbAllCounter = new StringBuilder();
                String strAllCounter = "";
                if ("clickhouse".equalsIgnoreCase(dbType)) {
                    lstGroupCounter.forEach(c -> {
                        sbAllCounter.append("AVG(c_").append(c.getCounterID()).append(") AS \"").append("c_")
                                .append(c.getCounterID()).append("\"").append(COMMA_CHAR).append(SPACE_CHAR);
                    });
                    strAllCounter = sbAllCounter.substring(0, sbAllCounter.length() - 2);
                }

                // KPI/Counter can hiện thi ra output
                List<String> lstColumnShow = new ArrayList<>();
                Set<String> column = new HashSet<>();

                if (!inputStatisticData.isShowRecentCounter()) {
                    lstGroupCounter.removeIf(e -> lstInputCounterKpiID.contains(e.getCounterID()));
                }

                lstGroupCounter.forEach(c -> {
                    lstColumnShow.add("round(" + c.getFormula() + ",2) AS \"" +
                            (inputStatisticData.isReturnCounterKpiId() ? c.getCounterID() : c.getName()) +
                            "\" ");
                    column.add(inputStatisticData.isReturnCounterKpiId() ? String.valueOf(c.getCounterID()) : c.getName());
                });

                Set<KpiFormula> sGroupKpiFormulas = hmGroupKPI.get(k);
                if (sGroupKpiFormulas != null) {
                    sGroupKpiFormulas.forEach(item -> {
                        String formula = item.getFormula();
                        lstColumnShow.add(
                                "round(" + formula + ",2) AS \"" +
                                        (inputStatisticData.isReturnCounterKpiId() ? item.getKpiID()
                                                : (item.getKpiName() + " (" + item.getUnits() + ")"))
                                        +
                                        "\" ");
                        column.add(inputStatisticData.isReturnCounterKpiId() ? String.valueOf(item.getKpiID())
                                : (item.getKpiName() + " (" + item.getUnits() + ")"));
                    });
                }

                String strSQLOutputCounterKpi = lstColumnShow.toString();
                strSQLOutputCounterKpi = strSQLOutputCounterKpi.substring(1, strSQLOutputCounterKpi.length() - 1);

                // Hour range generate
                StringBuilder timeRangeSQL = new StringBuilder();
                if (inputStatisticData.getRanges() != null) {
                    timeRangeSQL = getStringBuilderTimeRange(inputStatisticData);
                }

                // Build SQL
                try {
                    if ("clickhouse".equalsIgnoreCase(dbType)) {
                        String sqlQuery = createSQLClickHouse(finalSDuration,
                                fromTime, toTime, finalListNe,
                                sbExtraFieldFilter.toString(),
                                strAllCounter,
                                strSQLOutputCounterKpi,
                                inputStatisticData.getInterval(),
                                inputStatisticData.getIntervalUnit(),
                                k,
                                timeRangeSQL.toString(),
                                finalPrefix,
                                sbSelect.toString(),
                                sbGroupBy.toString(),
                                sbOrderBy.toString(),
                                sbKeyGroupBy.toString());
                        queryObject.setSql(sqlQuery);
                        queryObject.setColumn(column);
                        lstQueryObjects.add(queryObject);
                    } else {
                        // String sqlQuery = createSQLMySQL(finalSDuration,
                        // fromTime, toTime, finalListNe,
                        // sbExtraFieldFilter.toString(),
                        // strAllCounter,
                        // strSQLOutputCounterKpi,
                        // inputStatisticData.getInterval(),
                        // inputStatisticData.getIntervalUnit(),
                        // k,
                        // timeRangeSQL.toString(),
                        // finalPrefix,
                        // sbSelect.toString(),
                        // sbGroupBy.toString(),
                        // sbOrderBy.toString());
                        // queryObject.setSql(sqlQuery);
                        // queryObject.setColumn(column);
                        // lstQueryObjects.add(queryObject);
                    }
                } catch (ParseException e) {
                    log.error(e.getMessage());
                    log.error("Format date problem :{}", toTime);
                }
            });
        } else {
            log.error("Number of Group not equal.");
        }

        boolean isExistedKpiFromMultiTbl = !finalLstKpiFormulaWithMultiTable.isEmpty();

        if (isExistedKpiFromMultiTbl) {
            handleKpiMutiTable(finalLstKpiFormulaWithMultiTable, lstAllKpi, lstAllCounter, dbType, lstQueryObjects,
                    lstAllGroupCounterCounter, inputStatisticData, hmExtraField, hmKeyField);
        }
        CustomOutput<List<QueryObject>> output = new CustomOutput<>();
        output.setFuncOutput(lstQueryObjects);
        output.setCustomStringOutput("Total SQL Query: " + lstQueryObjects.size());
        return output;
    }

    private void handleKpiMutiTable(List<KpiFormula> lstKpiFormulasWithMultiTable, List<KpiFormula> lstAllKpi,
            List<Counter> lstAllCounter,
            String dbType,
            List<QueryObject> lstQueryObjects, List<GroupCounterCounter> lstAllGroupCounterCounter,
            InputStatisticData inputStatisticData,
            LinkedHashMap<String, ExtraFieldObject> hmExtraField, HashMap<String, ExtraFieldObject> hmKeyField) {

        // flatten all the KPI( or recursive KPI)
        Set<Integer> flattenAllInputKpiCounters = flatAllCounterKpi(lstKpiFormulasWithMultiTable, lstAllKpi);

        // Optimize: Combine multiple stream operations into one
        Set<Counter> allInputCounters = lstAllCounter.stream()
                .filter(c -> flattenAllInputKpiCounters.contains(c.getCounterID()))
                .collect(Collectors.toSet());

        Set<Integer> allInputCounterIds = allInputCounters.stream()
                .map(Counter::getCounterID)
                .collect(Collectors.toSet());

        Set<KpiFormula> allInputKpiObj = lstAllKpi.stream()
                .filter(kpi -> flattenAllInputKpiCounters.contains(kpi.getKpiID()))
                .collect(Collectors.toSet());

        log.debug("flattenAllKpiCounters - All KPI and Counter from request which is flatten: {}",
                flattenAllInputKpiCounters);
        log.debug("allInputKpiObj - All input KPI: {}", allInputKpiObj);
        log.debug("allInputCounters - All input counters: {}", allInputCounters);

        // Optimize: Pre-filter group counters once
        List<GroupCounterCounter> filterGroupGroupCounters = lstAllGroupCounterCounter.stream()
                .filter(g -> allInputCounterIds.contains(g.getCounterID())).collect(Collectors.toList());


        for (var kpi : lstKpiFormulasWithMultiTable) {
            // Lấy tất cả counter IDs từ KPI lồng nhau
            Set<Integer> arrCounters = new HashSet<>();
            Set<KpiFormula> arrKpi = new HashSet<>();
            if (kpi.getArrKpi() != null && !kpi.getArrKpi().trim().isEmpty()) {
                // Sử dụng hàm đệ quy để lấy tất cả counters
                Set<Integer> allRelatedIds = new HashSet<>();


                if (hasCircularDependency(kpi, lstAllKpi, new HashSet<>())) {
                    log.warn("Skipping KPI {} due to circular dependency", kpi.getKpiID());
                    continue; // Bỏ qua KPI này hoàn toàn
                }

                collectAllKpiAndCounters(kpi, allRelatedIds, new HashSet<>(), lstAllKpi);

                arrCounters.addAll(
                        allRelatedIds.stream()
                                .filter(allInputCounterIds::contains)
                                .collect(Collectors.toSet()));
                arrKpi.addAll(
                        allInputKpiObj.stream()
                                .filter(inputKpi -> allRelatedIds.contains(inputKpi.getKpiID()))
                                .collect(Collectors.toSet()));
            } else if (kpi.getArrCounter() != null && !kpi.getArrCounter().trim().isEmpty()) {
                arrCounters.addAll(Stream.of(kpi.getArrCounter().split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet()));
            }

            log.debug("All Counters ID from KPI: {}", arrCounters);
            log.debug("All KPI ID from KPI: {}", arrKpi);
            // Create final copy for lambda usage
            final Set<Integer> finalArrCounters = new HashSet<>(arrCounters);

        // Process each KPI
        for (var kpi1 : allInputKpiObj) {
            // FIXED: Get all counter IDs from the current KPI
            Set<Integer> arrCounters1 = extractCounterIdsFromKpi(kpi1);

            // Filter group counters for current KPI
            List<GroupCounterCounter> groupCounterCounters = filterGroupGroupCounters.stream()
                    .filter(g -> arrCounters1.contains(g.getCounterID()))
                    .toList();

            // Group by table and their columns
            Map<String, Set<Integer>> tblAndTheirColumnMap = groupCounterCounters.stream()
                    .collect(Collectors.groupingBy(GroupCounterCounter::getCode,
                            Collectors.mapping(GroupCounterCounter::getCounterID, Collectors.toSet())));

            log.info("tblAndTheirColumnMap: {}", tblAndTheirColumnMap);

            StringBuilder sRelatedCounters = new StringBuilder(" ");
            if (inputStatisticData.isShowRecentCounter()) {
                groupCounterCounters.forEach(c -> {
                    sRelatedCounters.append("round(SUM(c_").append(c.getCounterID())
                            .append(COMMA_CHAR).append(SPACE_CHAR).append("2) AS \"").append(c.getName()).append("\"")
                            .append(COMMA_CHAR);
                });
            }

            // Initialize SQL builders
            StringBuilder sbSelect = new StringBuilder("SELECT");
            StringBuilder sbGroupBy = new StringBuilder();
            StringBuilder sbOrderBy = new StringBuilder();
            StringBuilder sbKeyGroupBy = new StringBuilder();

            QueryObject queryObject = new QueryObject();
            Set<String> column = new HashSet<>();

            String listNe = inputStatisticData.getListNE().toString();
            listNe = listNe.substring(1, listNe.length() - 1);

            hmExtraField.forEach((key, value) -> {
                if (DATE_TIME_KEY.equalsIgnoreCase(key)) {
                    if ("clickhouse".equalsIgnoreCase(dbType)) {
                        sbSelect.append(SPACE_CHAR)
                                .append(getDateRoundClickHouse(true, inputStatisticData.getIntervalUnit(),
                                        inputStatisticData.getIntervalUnit(), ""))
                                .append(COMMA_CHAR);
                        sbOrderBy.append(SPACE_CHAR).append(value.getColumnName()).append(SPACE_CHAR).append("DESC")
                                .append(COMMA_CHAR);
                        sbGroupBy.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
                    } else {
                        log.warn("Only support clickhouse for now");
                    }
                } else {
                    sbSelect.append(SPACE_CHAR).append(PREFIX_JOIN_BASE_TABLE).append(".").append(value.getColumnName())
                            .append(" AS ").append(value.getColumnName()).append(COMMA_CHAR);
                    sbOrderBy.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
                    sbGroupBy.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR);
                }
            });

            sbSelect.append(sRelatedCounters);


            arrKpi.forEach(relatedKpi -> {
                String formula = relatedKpi.getFormula();
                sbSelect.append(SPACE_CHAR).append("round(").append(formula).append(",2) AS \"")
                        .append(inputStatisticData.isReturnCounterKpiId() ? relatedKpi.getKpiID()
                                : relatedKpi.getKpiName())
                        .append("\" ").append(COMMA_CHAR);
                column.add(inputStatisticData.isReturnCounterKpiId() ? String.valueOf(relatedKpi.getKpiID())
                        : relatedKpi.getKpiName());
            });

            hmKeyField.forEach(
                    (key, value) -> sbKeyGroupBy.append(SPACE_CHAR).append(value.getColumnName()).append(COMMA_CHAR));

            removeLastComma(sbSelect, sbGroupBy, sbOrderBy, sbKeyGroupBy);

            StringBuilder sbExtraFieldFilter = new StringBuilder();
            if (inputStatisticData.getOtherFilters() != null && !inputStatisticData.getOtherFilters().isEmpty()) {
                String otherFilter = OtherFilterUtil.generateExtraFilter(inputStatisticData.getOtherFilters(),
                        hmKeyField);
                sbExtraFieldFilter.append("AND").append(SPACE_CHAR).append("(").append(otherFilter).append(")");
            }

            String sDuration = calculateDuration(inputStatisticData);

            final String fromTime = inputStatisticData.getFromTime();
            final String toTime = isGTnow(inputStatisticData.getToTime())
                    ? inputStatisticData
                            .getToTime()
                    : sdf.format(Calendar.getInstance().getTime());

            String finalListNe = listNe;
            String finalPrefix = "";
            String finalSDuration = sDuration;
            boolean isFirstTable = true;
            int count = 0;
            int size = tblAndTheirColumnMap.size();
            for (var entry : tblAndTheirColumnMap.entrySet()) {
                count++;
                String joinTbl = entry.getKey();
                Set<Integer> counterIds = entry.getValue();

                List<Counter> lstGroupCounter = allInputCounters.stream()
                        .filter(item -> counterIds.contains(item.getCounterID()))
                        .toList();

                StringBuilder sbAllCounter = new StringBuilder();
                String strAllCounter = "";
                    lstGroupCounter.forEach(c -> {
                        sbAllCounter.append("AVG(c_").append(c.getCounterID()).append(") AS \"").append("c_")
                                .append(c.getCounterID()).append("\"").append(COMMA_CHAR).append(SPACE_CHAR);
                    });
                    strAllCounter = sbAllCounter.substring(0, sbAllCounter.length() - 2);

                List<String> lstColumnShow = new ArrayList<>();

                if (!inputStatisticData.isShowRecentCounter()) {

                    lstGroupCounter = new ArrayList<>();
                }

                lstGroupCounter.forEach(c -> {
                    lstColumnShow.add("round(" + c.getFormula() + ",2) AS \""
                            + (inputStatisticData.isReturnCounterKpiId() ? c.getCounterID() : c.getName()) + "\"");
                    column.add(inputStatisticData.isReturnCounterKpiId() ? String.valueOf(c.getCounterID()) : c.getName());
                });

                String strSQLoutputCounterKpi = lstColumnShow.toString();
                strSQLoutputCounterKpi = strSQLoutputCounterKpi.substring(1, strSQLoutputCounterKpi.length() - 1);

                StringBuilder timeRangeSQL = new StringBuilder();
                if (inputStatisticData.getRanges() != null) {
                    timeRangeSQL = getStringBuilderTimeRange(inputStatisticData);
                }

                try {
                    // don't need to check dbType because we only support clickhouse for now
                        String sqlQuery = createSubquerySQLClickhouseForMultiTable(isFirstTable,
                                count == size,
                                joinTbl,
                                finalSDuration,
                                fromTime,
                                toTime, finalListNe,
                                sbExtraFieldFilter.toString(),
                                strAllCounter,
                                inputStatisticData.getInterval(),
                                inputStatisticData.getIntervalUnit(),
                                joinTbl,
                                timeRangeSQL.toString(),
                                finalPrefix,
                                sbKeyGroupBy.toString());

                        sbSelect.append(sqlQuery);

                } catch (ParseException e) {
                    log.error(e.getMessage());
                    log.error("Format date problem: {}", toTime);
                }
                isFirstTable = false;
            }

            sbSelect.append("GROUP BY ").append(sbGroupBy).append(DOWN_LINE_CHAR);
            sbSelect.append("ORDER BY").append(sbOrderBy).append(SEMI_COLON_CHAR);

            queryObject.setSql(sbSelect.toString());
            queryObject.setColumn(column);
            lstQueryObjects.add(queryObject);

        }
    }

    }

    private boolean hasCircularDependency(KpiFormula kpiFormula, List<KpiFormula> lstAllKpi, Set<Integer> visited) {
        if (visited.contains(kpiFormula.getKpiID())) {
            return true; // Phát hiện circular dependency
        }

        visited.add(kpiFormula.getKpiID());

        if (kpiFormula.getArrKpi() != null && !kpiFormula.getArrKpi().trim().isEmpty()) {
            String[] arrKpiIds = kpiFormula.getArrKpi().split(",");
            for (String kpiIdStr : arrKpiIds) {
                try {
                    int kpiId = Integer.parseInt(kpiIdStr.trim());
                    KpiFormula relatedKpi = lstAllKpi.stream()
                            .filter(kpi -> kpi.getKpiID() == kpiId)
                            .findFirst()
                            .orElse(null);

                    if (relatedKpi != null && hasCircularDependency(relatedKpi, lstAllKpi, visited)) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    log.error("Invalid KPI ID format: {} in KPI {}", kpiIdStr, kpiFormula.getKpiID());
                }
            }
        }

        visited.remove(kpiFormula.getKpiID());
        return false;
    }

    private void removeLastComma(StringBuilder... builders) {
        for (StringBuilder sb : builders) {
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == COMMA_CHAR) {
                sb.setLength(sb.length() - 1);
            }
        }
    }

    private Set<Integer> extractCounterIdsFromKpi(KpiFormula kpi) {

        Set<Integer> counterIds = new HashSet<>();
        if (kpi.getArrCounter() != null && !kpi.getArrCounter().trim().isEmpty()) {
            String[] arrCounterIds = kpi.getArrCounter().split(",");
            for (String counterIdStr : arrCounterIds) {
                try {
                    Integer counterId = Integer.parseInt(counterIdStr.trim());
                    counterIds.add(counterId);
                } catch (NumberFormatException e) {
                    log.error("Invalid counter ID format: {} in KPI {}", counterIdStr, kpi.getKpiID());
                }
            }
        }
        return counterIds;
    }

    private String buildRelatedCountersString(List<GroupCounterCounter> groupCounterCounters, InputStatisticData inputStatisticData) {
        StringBuilder sb = new StringBuilder();
        groupCounterCounters.forEach(g -> {
            sb.append("round(SUM(c_").append(g.getCounterID()).append("),2) AS \"").append(g.getName()).append("\" ").append(COMMA_CHAR);
        });
        return sb.substring(0, sb.length() - 2);
    }


private String calculateDuration(InputStatisticData inputStatisticData) {
    if (inputStatisticData.getDuration() <= 0) {
        return "";
    }

    Integer multiplier = DURATION_MULTIPLIERS.get(inputStatisticData.getIntervalUnit());
    if (multiplier == null) {
        return "";
    }

    return " AND duration = " + (inputStatisticData.getDuration() * multiplier);
}

    private String createSubquerySQLClickhouseForMultiTable(boolean isFirstTable, boolean isLastTable,
            String joinTbl,

            String sDuration,
            String fromTime, String toTime, String neList, String extraFieldFilter, String allCounter, int interval,
            Integer intervalUnit, String tableName, String timeRange, String suffix, String sKeyGroupBy)
            throws ParseException {
        Date truncToTime = util.getRoundDate(sdf.parse(toTime), interval, intervalUnit);
        StringBuilder sql = new StringBuilder();

        if (isFirstTable) {
            sql.append(DOWN_LINE_CHAR).append("FROM ( ").append(DOWN_LINE_CHAR);
        }

        sql.append("SELECT record_time, ne_id");
        if (allCounter != null && !allCounter.isEmpty()) {
            sql.append(DOWN_LINE_CHAR).append(COMMA_CHAR).append(SPACE_CHAR).append(allCounter);
        }
        if (sKeyGroupBy != null && !sKeyGroupBy.isBlank()) {
            sql.append(COMMA_CHAR).append(SPACE_CHAR).append(sKeyGroupBy.trim());
        }
        sql.append(" FROM ").append(tableName).append(suffix).append(DOWN_LINE_CHAR);
        sql.append("WHERE record_time >= '").append(fromTime).append("' AND record_time < '")
                .append(sdf.format(truncToTime));
        sql.append("' ").append(timeRange).append(sDuration).append(" AND ne_id IN(").append(neList).append(")");
        sql.append(SPACE_CHAR).append(extraFieldFilter).append(
                DOWN_LINE_CHAR);
        sql.append("GROUP BY ").append(sKeyGroupBy.trim()).append(DOWN_LINE_CHAR);
        String firstLettersTbl = DBUtils.getFirstLettersTbl(joinTbl);
        sql.append(")").append(" AS ").append(isFirstTable ? PREFIX_JOIN_BASE_TABLE : firstLettersTbl);
        if (!isFirstTable) {
            String joinCondition = buildConditionJoinTbl(firstLettersTbl);
            sql.append(" ").append(joinCondition);
        }
        sql.append(!isLastTable ? " FULL JOIN (" : "");

        return sql.toString();

    }

    private String buildConditionJoinTbl(String firstLettersTbl) {
        if (firstLettersTbl == null || firstLettersTbl.trim().isEmpty()) {
            throw new IllegalArgumentException("Table alias cannot be null or empty");
        }

         final String[] joinColumns = {
                "record_time",
                "ne_id",
                "cell_name",
                "location",
                "cell_index"
        };

        StringBuilder sql = new StringBuilder();

        for (int i = 0; i < joinColumns.length; i++) {
            String column = joinColumns[i];

            if (i == 0) {
                sql.append("ON ");
            } else {
                sql.append("AND ");
            }

            // Build join condition: base.column = alias.column
            sql.append(PREFIX_JOIN_BASE_TABLE)
                    .append(".")
                    .append(column)
                    .append(" = ")
                    .append(firstLettersTbl)
                    .append(".")
                    .append(column)
                    .append(DOWN_LINE_CHAR);
        }
        return sql.toString();
    }

    private Set<Integer> flatAllCounterKpi(List<KpiFormula> kpiFormulas, List<KpiFormula> lstAllKpi) {
        Set<Integer> allKpiCounters = new HashSet<>();
        Set<Integer> processedKpiIds = new HashSet<>();

        for (var kpi : kpiFormulas) {
            collectAllKpiAndCounters(kpi, allKpiCounters, processedKpiIds, lstAllKpi);
        }

        return allKpiCounters;
    }

    private void collectAllKpiAndCounters(KpiFormula kpiFormula, Set<Integer> allKpiCounters,
            Set<Integer> processedKpiIds, List<KpiFormula> lstAllKpi) {
        // Prevent infinite recursion
        if (processedKpiIds.contains(kpiFormula.getKpiID())) {
//            log.warn("Circular dependency detected for KPI ID: {} - Skipping this KPI and its dependencies", kpiFormula.getKpiID());

            // Chỉ xóa KPI hiện tại
            allKpiCounters.remove(kpiFormula.getKpiID());

            // Xóa tất cả KPI đang được xử lý trong call stack (cha, ông, cụ...)
            for (Integer kpiId : processedKpiIds) {
                allKpiCounters.remove(kpiId);
            }

            return;
        }

        processedKpiIds.add(kpiFormula.getKpiID());

        try {
            // Add current KPI ID
            allKpiCounters.add(kpiFormula.getKpiID());

            // Process arrCounter if exists
            if (kpiFormula.getArrCounter() != null && !kpiFormula.getArrCounter().trim().isEmpty()) {
                String[] arrCounterIds = kpiFormula.getArrCounter().split(",");
                for (String counterIdStr : arrCounterIds) {
                    try {
                        Integer counterId = Integer.parseInt(counterIdStr.trim());
                        allKpiCounters.add(counterId);
                    } catch (NumberFormatException e) {
                        log.error("Invalid counter ID format: {} in KPI {}", counterIdStr, kpiFormula.getKpiID());
                    }
                }
            }

            // Process arrKpi if exists (recursive)
            if (kpiFormula.getArrKpi() != null && !kpiFormula.getArrKpi().trim().isEmpty()) {
                String[] arrKpiIds = kpiFormula.getArrKpi().split(",");
                for (String kpiIdStr : arrKpiIds) {
                    try {
                        Integer kpiId = Integer.parseInt(kpiIdStr.trim());
                        allKpiCounters.add(kpiId);

                        // Tìm KPI liên quan và xử lý đệ quy
                        KpiFormula relatedKpi = lstAllKpi.stream()
                                .filter(kpi -> kpi.getKpiID() == kpiId)
                                .findFirst()
                                .orElse(null);

                        if (relatedKpi != null) {
                            collectAllKpiAndCounters(relatedKpi, allKpiCounters, processedKpiIds, lstAllKpi);
                        } else {
                            log.warn("Related KPI not found for ID: {}", kpiId);
                        }
                    } catch (NumberFormatException e) {
                        log.error("Invalid KPI ID format: {} in KPI {}", kpiIdStr, kpiFormula.getKpiID());
                    }
                }
            }
        } finally {
            // Quan trọng: Luôn xóa KPI khỏi processedKpiIds, kể cả khi có exception
            processedKpiIds.remove(kpiFormula.getKpiID());
        }
    }


  private void collectAllKpiAndCounters(KpiFormula kpiFormula, Set<Integer> allKpiCounters,
                                          Set<Integer> processedKpiIds, List<KpiFormula> lstAllKpi, Set<Integer> childKpiIds) {
        // Prevent infinite recursion
        if (processedKpiIds.contains(kpiFormula.getKpiID())) {
//            log.warn("Circular dependency detected for KPI ID: {} - Skipping this KPI and its dependencies", kpiFormula.getKpiID());

            // Chỉ xóa KPI hiện tại
            allKpiCounters.remove(kpiFormula.getKpiID());

            // Xóa tất cả KPI đang được xử lý trong call stack (cha, ông, cụ...)
            for (Integer kpiId : processedKpiIds) {
                allKpiCounters.remove(kpiId);
            }

            return;
        }

        processedKpiIds.add(kpiFormula.getKpiID());

        try {
            // Add current KPI ID
            allKpiCounters.add(kpiFormula.getKpiID());

            // Process arrCounter if exists
            if (kpiFormula.getArrCounter() != null && !kpiFormula.getArrCounter().trim().isEmpty()) {
                String[] arrCounterIds = kpiFormula.getArrCounter().split(",");
                for (String counterIdStr : arrCounterIds) {
                    try {
                        Integer counterId = Integer.parseInt(counterIdStr.trim());
                        allKpiCounters.add(counterId);
                    } catch (NumberFormatException e) {
                        log.error("Invalid counter ID format: {} in KPI {}", counterIdStr, kpiFormula.getKpiID());
                    }
                }
            }

            // Process arrKpi if exists (recursive)
            if (kpiFormula.getArrKpi() != null && !kpiFormula.getArrKpi().trim().isEmpty()) {
                String[] arrKpiIds = kpiFormula.getArrKpi().split(",");
                for (String kpiIdStr : arrKpiIds) {
                    try {
                        Integer kpiId = Integer.parseInt(kpiIdStr.trim());
                        allKpiCounters.add(kpiId);

                        // Tìm KPI liên quan và xử lý đệ quy
                        KpiFormula relatedKpi = lstAllKpi.stream()
                                .filter(kpi -> kpi.getKpiID() == kpiId)
                                .findFirst()
                                .orElse(null);

                        if (relatedKpi != null) {
                            childKpiIds.add(kpiId);
                            collectAllKpiAndCounters(relatedKpi, allKpiCounters, processedKpiIds, lstAllKpi, childKpiIds);
                        } else {
                            log.warn("Related KPI not found for ID: {}", kpiId);
                        }
                    } catch (NumberFormatException e) {
                        log.error("Invalid KPI ID format: {} in KPI {}", kpiIdStr, kpiFormula.getKpiID());
                    }
                }
            }
        } finally {
            // Quan trọng: Luôn xóa KPI khỏi processedKpiIds, kể cả khi có exception
            processedKpiIds.remove(kpiFormula.getKpiID());
        }
    }

    private void findChildKpis(KpiFormula kpiFormula, List<KpiFormula> lstAllKpi,
                          Set<Integer> childKpiIds, Set<Integer> visited) {
    // Prevent infinite recursion
    if (visited.contains(kpiFormula.getKpiID())) {
        return;
    }

    visited.add(kpiFormula.getKpiID());

    // Tìm tất cả KPI con (KPI được sử dụng trong arrKpi)
    if (kpiFormula.getArrKpi() != null && !kpiFormula.getArrKpi().trim().isEmpty()) {
        String[] arrKpiIds = kpiFormula.getArrKpi().split(",");
        if(arrKpiIds.length ==0) {
            childKpiIds.remove(kpiFormula.getKpiID());
        }
        for (String kpiIdStr : arrKpiIds) {
            try {
                int kpiId = Integer.parseInt(kpiIdStr.trim());
                childKpiIds.add(kpiId); // Đây là KPI con

                // Đệ quy tìm KPI con của KPI con
                KpiFormula childKpi = lstAllKpi.stream()
                        .filter(kpi -> kpi.getKpiID() == kpiId)
                        .findFirst()
                        .orElse(null);

                if (childKpi != null) {
                    findChildKpis(childKpi, lstAllKpi, childKpiIds, visited);
                }
            } catch (NumberFormatException e) {
                log.error("Invalid KPI ID format: {} in KPI {}", kpiIdStr, kpiFormula.getKpiID());
            }
        }
    }

    visited.remove(kpiFormula.getKpiID());
}





    private StringBuilder getStringBuilderTimeRange(InputStatisticData inputStatisticData) {
        StringBuilder timeRangeSQL = new StringBuilder();
        List<TimeRange> ranges = inputStatisticData.getRanges();
        if (ranges != null && !ranges.isEmpty()) {
            timeRangeSQL.append("AND (");
            for (int idx = 0; idx < ranges.size(); idx++) {
                TimeRange timeRange = ranges.get(idx);
                long hourStart = Long.parseLong(timeRange.getStart().split(":")[0]) * 60 * 60 * 1000;
                long minStart = Long.parseLong(timeRange.getStart().split(":")[1]) * 60 * 1000;
                long hourEnd = Long.parseLong(timeRange.getEnd().split(":")[0]) * 60 * 60 * 1000;
                long minEnd = Long.parseLong(timeRange.getEnd().split(":")[1]) * 60 * 1000;

                if (idx != 0) {
                    timeRangeSQL.append("OR ");
                }

                timeRangeSQL.append(
                        "(EXTRACT(hour FROM record_time)*60*60*1000 + EXTRACT(minute FROM record_time)*60*1000 + " +
                                "EXTRACT(second FROM record_time)*1000 >= ")
                        .append(hourStart + minStart)
                        .append(" AND ")
                        .append("(EXTRACT(hour FROM record_time)*60*60*1000 + EXTRACT(minute FROM record_time)*60*1000 + "
                                +
                                "EXTRACT(second FROM record_time)*1000 < ")
                        .append(hourEnd + minEnd)
                        .append(")");
            }
            timeRangeSQL.append(")");
        }
        return timeRangeSQL;
    }


    private String createSQLClickHouse(
            String sDuration,
            String fromTime,
            String toTime,
            String neList,
            String extraFieldFilter,
            String allCounter,
            String columnShow,
            int interval,
            Integer intervalUnit,
            String tableName,
            String timeRange,
            String suffix,
            String sSelect,
            String sGroupBy,
            String sOrderBy,
            String sKeyGroupBy) throws ParseException {
        Date truncToTime = util.getRoundDate(sdf.parse(toTime), interval, intervalUnit);
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(sSelect).append(DOWN_LINE_CHAR);
        sql.append("(").append(DOWN_LINE_CHAR);
        sql.append("SELECT ").append(sSelect).append(DOWN_LINE_CHAR);
        sql.append(COMMA_CHAR).append(allCounter).append(DOWN_LINE_CHAR);
        sql.append("FROM ").append(tableName).append(suffix).append(DOWN_LINE_CHAR);
        sql.append("WHERE record_time >= '").append(fromTime).append("' AND record_time < '")
                .append(sdf.format(truncToTime));
        sql.append("' ").append(timeRange).append(sDuration).append(" AND ne_id IN (").append(neList).append(")");
        sql.append(SPACE_CHAR).append(extraFieldFilter).append(DOWN_LINE_CHAR);
        sql.append("GROUP BY ").append(sKeyGroupBy).append(DOWN_LINE_CHAR);
        sql.append(")").append(DOWN_LINE_CHAR);
        sql.append("GROUP BY ").append(sGroupBy).append(DOWN_LINE_CHAR);
        sql.append("ORDER BY ").append(sOrderBy).append(SEMI_COLON_CHAR);
        return sql.toString();
    }

    private String getDateRoundMySQL(int interval, Integer intervalUnit) {
        if (intervalUnit.equals(IntervalUnit.MINUTE.getValue())) {
            if (interval == 5 || interval == 15 || interval == 30 || interval == 60) {
                return "DATE_ADD(DATE_FORMAT(record_time,'%Y-%m-%d %H:00:00'), INTERVAL FLOOR(minute(record_time)/("
                        + interval + ")) *" + interval + " minute) AS report_time";
            } else if (interval == 1440) {
                return "DATE_FORMAT(record_time,'%Y-%m-%d 00:00:00') AS report_time";
            }
        } else if (intervalUnit.equals(IntervalUnit.SECOND.getValue())) {
            return "DATE_ADD(DATE_FORMAT(record_time,'%Y-%m-%d %H:%i:00'), INTERVAL FLOOR(second(record_time)/("
                    + interval + ")) *" + interval + " second) AS report_time";
        } else if (intervalUnit.equals(IntervalUnit.HOUR.getValue())) {
            return "DATE_ADD(DATE_FORMAT(record_time,'%Y-%m-%d 00:00:00'), INTERVAL FLOOR(hour(record_time)/("
                    + interval + ")) *" + interval + " hour) AS report_time";
        } else if (intervalUnit.equals(IntervalUnit.DATE.getValue())) {
            return "DATE_FORMAT(record_time,'%Y-%m-%d 00:00:00') AS report_time";
        } else if (intervalUnit.equals(IntervalUnit.WEEK.getValue())) {
            return "DATE_ADD(DATE_FORMAT(record_time,'%Y-%m-01 00:00:00'), INTERVAL FLOOR(week(record_time)/("
                    + interval + ")) *" + interval + " week) AS report_time";
        } else if (intervalUnit.equals(IntervalUnit.MONTH.getValue())) {
            return "DATE_ADD(DATE_FORMAT(record_time,'%Y-01-00 00:00:00'), INTERVAL FLOOR(month(record_time)/("
                    + interval + ")) *" + interval + " month) AS report_time";
        }
        return "report_time";
    }

    private String getDateRoundClickHouse(boolean isQueryMultiTbl, int interval, Integer intervalUnit, String prefix) {
        StringBuilder recordTimeString = new StringBuilder("formatDateTime(");
        if (intervalUnit.equals(IntervalUnit.MINUTE.getValue())) {
            if (interval == 5 || interval == 15 || interval == 30 || interval == 60) {
                recordTimeString.append("toStartOfMinute(toDateTime(").append(prefix)
                        .append("record_time)) + INTERVAL " + interval + " MINUTE");
            } else if (interval == 1440) {
                recordTimeString.append("toStartOfDay(toDateTime(").append(prefix).append("record_time))");
            }
        } else if (intervalUnit.equals(IntervalUnit.SECOND.getValue())) {
            recordTimeString.append("toStartOfSecond(toDateTime(").append(prefix)
                    .append("record_time)) + INTERVAL " + interval + " SECOND");
        } else if (intervalUnit.equals(IntervalUnit.HOUR.getValue())) {
            recordTimeString.append("toStartOfHour(toDateTime(").append(prefix)
                    .append("record_time)) + INTERVAL " + interval + " HOUR");
        } else if (intervalUnit.equals(IntervalUnit.DATE.getValue())) {
            recordTimeString.append("toStartOfDay(toDateTime(").append(prefix).append("record_time))");
        } else if (intervalUnit.equals(IntervalUnit.WEEK.getValue())) {
            recordTimeString.append("toMonday(toDateTime(").append(prefix)
                    .append("record_time)) + INTERVAL " + interval + " WEEK");
        } else if (intervalUnit.equals(IntervalUnit.MONTH.getValue())) {
            recordTimeString.append("toStartOfMonth(toDateTime(").append(prefix)
                    .append("record_time)) + INTERVAL " + interval + " MONTH");
        }
        recordTimeString.append(")");
        return recordTimeString.toString();
    }

}
