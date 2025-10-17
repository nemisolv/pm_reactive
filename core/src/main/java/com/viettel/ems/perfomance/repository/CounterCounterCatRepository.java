package com.viettel.ems.perfomance.repository;


import com.viettel.ems.perfomance.config.ConfigManager;
import com.viettel.ems.perfomance.config.RoutingContextExecutor;
import com.viettel.ems.perfomance.config.SystemType;
import com.viettel.ems.perfomance.config.TenantContextHolder;
import com.viettel.ems.perfomance.object.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@Slf4j
@RequiredArgsConstructor
public class CounterCounterCatRepository {
    private final JdbcTemplate jdbcTemplateMySQL;
    private final RoutingContextExecutor routingContextExecutor;
    private final ConfigManager configManager;


    @Value("spring.data-lake.counter_cat_id")
    private String counterCatIds;
    @Value("${spring.pm.clickhosue_cluster}")
    private String clickHouseCluster;
    @Value("${spring.kafka.producer.bootstrap-address}")
    private String kafkaBootStrapAddress;


    @postConstruct
    private void setClickhouseCluster() {
        if(!clickhouseCluster.equals("none")) {
            clickHouseCluster = " on cluster " + clickHouseCluster;
        }else {
            clickhouseCluster = "";
        }
    }

    public List<CounterCounterCatObject> findAll() {
        List<CounterCounterCatObject> result ;
        final String sql = "SELECT c.id, c.counter_cat_id, cc.code, cc.object_level_id, c.is_sub_kpi, c.sk_cat_id, c.kpi_type \n" +
                "FROM counter c, counter_cat cc\n" +
                "WHERE c.status = 1 and ((c.counter_cat_id = c.id and is_kpi = 0) or (c.is_sub_kpi = 1 and c.sk_cat_id = cc.id)) \n" +
                "ORDER BY c.id;";
        try {
            result = jdbcTemplateMySQL.query(sql, (rs, _row) -> CounterCounterCatObject.fromRs(rs));
            return result;
        }catch (Exception e){
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public HashMap<String, CounterConfigObject> findCounterOran() {
        try {
            final String sql = "Select id, name, position, mesurement_identifier, mesurement_object_measurement_group \n" +
                    "FROM counter \n" +
                    "WHERE is_kpi = 0 and position IS NOT NULL \n" +
                    "ORDER BY measurement_group, measurement_object, position;";
            HashMap<String, CounterConfigObject> result = new HashMap<>();
            setRsCounterOran(sql, result, jdbcTemplateMySQL);
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new HashMap<>();
        }
    }

    private void setRsCounterOran(String sql, HashMap<String, CounterConfigObject> result, JdbcTemplate jdbcTemplateMySQL) {
        jdbcTemplateMySQL.query(sql,
                rs -> {
                    int id = rs.getInt("id");
                    String name = rs.getString("name").trim();
                    int position = rs.getInt("position");
                    int measurementIdentifier = rs.getInt("measurement_identifier");
                    String measurementObject = rs.getString("measurement_object").trim().toUpperCase();
                    String measurementGroup = rs.getString("measurement_group").trim();
                    String measurementKey = measurementIdentifier + "_" +  measurementObject;
                    LiteCounterObject liteCounterObject = LiteCounterObject.builder()
                            .id(id)
                            .name(name)
                            .position(position)
                            .build();
                    if(result.containsKey(measurementKey)) {
                        result.get(measurementKey).getHmLiteCounter().put(position, liteCounterObject);
                    }else {
                        HashMap<Integer, LiteCounterObject> hmLiteCounter = new HashMap<>();
                        hmLiteCounter.put(position, liteCounterObject);
                        CounterConfigObject counterConfigObject = CounterConfigObject.builder()
                                .measurementIdentifier(measurementIdentifier)
                                .measurementObject(measurementObject)
                                .measurementGroup(measurementGroup)
                                .hmLiteCounter(hmLiteCounter)
                                .build();
                        result.put(measurementKey,counterConfigObject);
                    }
                });
    }

    public Set<String> getListCounterCatCodeONT() {
        Set<String> codes = new HashSet<>();
        jdbcTemplateMySQL.query(String.format("select code from counter_cat where id in (%s)", counterCatIds),
                (rs) -> {
                        String code = rs.getString("code");
                        if(!Objects.equals(null, code)) {
                            codes.add(code);
                        }
                });
                return codes;
    }


    public List<String> getTableHeader(String tableName) {
        String query = "select * from " + tableName + " limit 1";
        List<String> header = new ArrayList<>();
        SystemType systemType = TenantContextHolder.getCurrentSystem();
        String dsKey = TenantContextHolder.getCurrentDatasourceKey();
        routingContextExecutor.runWith(systemType, "CLICKHOUSE", () -> {
            jdbcTemplateMySQL.query(query, rs -> {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                for(int i = 1;i <= columnCount;i++) {
                    header.add(metaData.getColumnName(i));
                }
                return null;
            });
            log.info("[getTableHeader] tableName: " + tableName);
        });
        return header;
    }

    public Map<String, String> getCounterCatNamesMappedByCounterColumn(Map<String, List<String> > hmHeaders) {
        List<Integer> ids = new ArrayList<>();
        for(List<String> table : hmHeaders.values()) {
            for(String header : table) {
                if(header.toLowerCase().startsWith("c_")) {
                    try {
                        Integer id = Integer.parseInt(header.toLowerCase().replace("c_", ""));
                        ids.add(id);
                    }catch (Exception e) {

                    }
                }
            }
        }
        return null;
    }

    public List<CounterCatObject> getUpdatedCounterCat(String lastTimeUpdate) {
        List<CounterCatObject> counterCatObjects = new ArrayList<>();
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("select id, code, object_level_id, is_sub_kpi_cat from counter_cat");
            if(lastTimeUpdate !=  null) {
                sql.append(String.format(" where (created-date >= '%s' or updated_date >= '%s');", lastTimeUpdate, lastTimeUpdate));
            }
            jdbcTemplateMySQL.query(sql.toString(), (rs) -> {
                CounterCatObject counterCatObject = null;
                try {
                    counterCatObject = CounterCatObject.fromRs(rs);
                }catch (Exception e) {
                    log.error("Eror while adding counter cat to list: {}", e.getMessage(), e);
                }
                counterCatObjects.add(counterCatObject);
            });
            counterCatObjects.removeIf(Objects::isNull);
        }catch (Exception e) {
            log.error("Error while getting counter cat list: {}", e.getMessage(), e);
        }
        return counterCatObjects;
    }

//    public List<CounterCounterCatObject> getUpdatedCounterCat(String lastTimeUpdate) {
//        List<CounterCounterCatObject> counterCatObjects = new ArrayList<>();
//        try {
//            StringBuilder sql = new StringBuilder();
//            sql.append("select c.id, c.counter_cat_id, cc.code, cc.object_level_id, c.is_sub_kpi, c.sk_cat_id, c.kpi_type " +
//                    "from counter c,counter_cat cc" +
//                    "where (is_kpi = 0 or is_sub_kpi = 1) and c.status = 1 and (( c.counter_cat_id = cc.id and is_kpi = 0) or (c.is_sub_kpi = 1 and c.sk_cat_id"
//
//
//                    );
//        }catch (Exception e) {
//            log.error("Error while getting counter cat list: {}", e.getMessage(), e);
//        }
//        return counterCatObjects;
//    }


//    public void addCounterCatTableToDb(String counterCatCode, HashMap<String, ExtraFieldObject> extraFieldObjectHashMap, List<Integer> counterIdList, boolean isSubCat) {
//        StringBuilder sql = new StringBuilder();
//        boolean isUsingClickhouse = configManager.getCustomBoolean(TenantContextHolder.getCurrentSystem(), "isUsingClickhouse");
//        if(isUsingClickhouse) {
//            String clickHouseDatabase = configManager.getCustomValue(TenantContextHolder.getCurrentSystem(), "clickHouseDatabase");
//            sql.append("CREATE TABLE IF NOT EXISTS ").append(clickhouseCluster);
//            String tableColumns = getTableColumnString(extraFieldObjectHashMap, counterIdList, false, isSubCat);
//            sql.append(tableColumns);
//            if(clickhouseCluster.equals("")) {
//                sql.append(" ENGINE = ReplacingMergeTree");
//            }else {
//                sql.append(" ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/")
//                        .append(clickHouseDatabase)
//                        .append("/{uuid}', '{replica}')");
//            }
//            sql.append(" PARTITION BY toYYYYMM(record_time)")
//                    .append(" PRIMARY KEY (ne_id, record_time");
//            if(extraFieldObjectHashMap != null && !extraFieldObjectHashMap.isEmpty())
//                sql.append(", ").append(extraFieldObjectHashMap.values().stream().map(extraFieldObject ->
//                        String.format("`%s`", extraFieldObject.getColumnName()))
//                        .collect(Collectors.joining()))
//                        ;
//        }
//    }
//









}
