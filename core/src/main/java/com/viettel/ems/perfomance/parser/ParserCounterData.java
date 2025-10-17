package com.viettel.ems.perfomance.parser;

import com.viettel.ems.perfomance.object.*;
import com.viettel.ems.perfomance.object.clickhouse.NewFormatCounterObject;
import com.viettel.ems.perfomance.service.ProcessDataONT;
import com.viettel.ems.perfomance.tools.CounterSchema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.capnproto.MessageReader;
import org.capnproto.ReaderOptions;
import org.capnproto.Serialize;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.*;

@Slf4j
@Data
@NoArgsConstructor
public class ParserCounterData {
    private static final ReaderOptions readerOptions = new ReaderOptions(1024L * 1024L * 8L * 1000L, 128);
    private Map<String, NEObject> neObjectMap = new HashMap<>();
    private Map<Integer, CounterCounterCatObject> counterCounterCatObjectMap = new HashMap<>();
    private Map<Integer, HashMap<String, ExtraFieldObject>> extraFieldObjectMap = new HashMap<>();

    private HashMap<String, CounterConfigObject> counterConfigObjectMap = new HashMap<>();

    private final int RX_TX_WINDOW_TRANSPORT_LENGTH = 8;
    private final int TRANSCEIVER_LENGTH = 28;
    private final int EPE_LENGTH = 8;
    private final int FILE_NAME_LENGTH = 7;
    private final int START_INDEX = 4;

    private Map<String, Integer> hmColumnCodeExtraField;

    public ParserCounterData(HashMap<String, NEObject> neObjectMap,
                             Map<Integer, CounterCounterCatObject> counterCounterCatObjectMap,
                             Map<Integer, HashMap<String, ExtraFieldObject>> extraFieldObjectMap,
                             HashMap<String, CounterConfigObject> counterConfigObjectMap,
                             Map<String, Integer> hmColumnCodeExtraField
                             ) {
        this.neObjectMap = neObjectMap;
        this.counterCounterCatObjectMap = counterCounterCatObjectMap;
        this.extraFieldObjectMap = extraFieldObjectMap;
        this.counterConfigObjectMap = counterConfigObjectMap;
        this.hmColumnCodeExtraField = hmColumnCodeExtraField;
    }


    public ArrayList<CounterObject> parseCounter(CounterDataObject preCounter) {
        ArrayList<CounterObject> lstCounter = new ArrayList<>();
        try {
            String fileName = preCounter.getFileName().toUpperCase();
            //gNodeB_gHI04305_1616233232_2332_4fdsfasasdf           --> Access(gNodeB)
            //A20210323.0953+2323-2332+2323_SMF_-_SMF01              --> Core (AMF, SMF, DMF, UMF...)
            if (fileName.contains("GNODEB")) {
                if (fileName.contains("_GNODEB_") && fileName.contains("RU")) {
                    lstCounter = parseCounter5GAccessCSV(preCounter);
                } else if (fileName.contains("_GNODEB_")) {
                    lstCounter = parseCounter5GAccessProtobuf(preCounter);
                } else {
                    lstCounter = parseCounter5GAccessCapnProto(preCounter);
                }
            } else if (fileName.contains("AMF") ||
                    fileName.contains("SMF") ||
                    fileName.contains("UPF") ||
                    fileName.contains("PCF") ||
                    fileName.contains("AUSF") ||
                    fileName.contains("NSSF")
            ) {
                lstCounter = parseCounter5GCoreProtobuf(preCounter);
            } else if (
                    fileName.contains("ONT")
            ) {
                lstCounter = parseCounterONTJSON(preCounter);
            } else {
                log.warn("⚠️ Unrecognized file format for file: {}", fileName);
            }

        } catch (Exception ex) {
            log.error("❌ Error parsing counter data from file {}: {}", preCounter.getFileName(), ex.getMessage());
            return null;
        }


        return new ArrayList<>(lstCounter);
    }

    private List<CounterObject> parseCounterONTJSON(CounterDataObject preCounter) {
        return null;
    }

    private List<CounterObject> parseCounter5GCoreProtobuf(CounterDataObject preCounter) {
        return null;
    }

    private List<CounterObject> parseCounter5GAccessCapnProto(CounterDataObject preCounter) {
        List<CounterObject> lstCounter = new ArrayList<>();
        try {
            ByteBuffer capMsg = preCounter.getBufferData();
            capMsg.rewind();
            MessageReader rd = Serialize.read(capMsg, readerOptions);
            CounterSchema.CounterDataCollection.Reader rd1 = rd.getRoot(CounterSchema.CounterDataCollection.factory);
            String neName = rd1.getUnit().toString();
            if (neObjectMap.get(neName) == null) {
                log.warn("Oops! Cannot find NodeName: {}! Ignore counter file{}", neName, preCounter.getFileName());
                return null;
            }
            int neId = neObjectMap.get(neName).getId();
            int totalCount = 0;
            //CounterData
            for (CounterSchema.CounterData.Reader r : rd1.getData()) {
                totalCount++;
                Timestamp recordTime = new Timestamp(r.getTime());
                int duration = r.getDuration();
                String location = r.getLocation().toString().trim();
                long cellIndex = r.getCell();
                int beginNewLocationIdx = 0;
                StringBuilder counterValue = new StringBuilder();
                // Get extrafield from file

                HashMap<String, String> hmExtraFieldFile = parseMeasObj(location);
                if (!location.trim().isEmpty()) {
                    hmExtraFieldFile.put("location", location);
                }
                hmExtraFieldFile.put("cell_index", String.valueOf(cellIndex));

                // put to column ne
                String ratType = RatType.fromNodeFunction(hmExtraFieldFile.get("nodefunction"));
                hmExtraFieldFile.put("rat_type", ratType);

                hmExtraFieldFile.put("cell_name", hmExtraFieldFile.get("cellname"));


                Integer objLevelId = hmColumnCodeExtraField.get("rat_type");

                // Countervalue
                for (CounterSchema.CounterValue.Reader r2 : r.getData()) {
                    beginNewLocationIdx++;
                    counterValue.append("(").append(r2.getId()).append(": ").append(r2.getValue()).append("), ");
                    //Create counter object
                    CounterObject counterObj = new CounterObject();
                    //set basic fields
                    counterObj.setTime(recordTime);
                    counterObj.setNeId(neId);
                    counterObj.setDuration(duration);
                    counterObj.setCounterId(r2.getId());
                    counterObj.setCounterValue(r2.getValue());
                    counterObj.setRatType(ratType)

                    //step 2: separating counter into groups
                    String key = CounterCounterCatObject.buildCounterCounterCatKey(counterObj.getCounterId(), String.valueOf(objLevelId));
                    if (counterCounterCatObjectMap.containsKey(key)) {
                        CounterCounterCatObject ccco = counterCounterCatObjectMap.get(key);
                       HashMap<String, ExtraFieldObject> hmExtraFieldConfig = extraFieldObjectMap.getOrDefault(ccco.getObjectLevelId(), new HashMap<>());
                        // Filter extra_field: leave extra_field not in config
                        HashMap<String, LiteExtraFieldObject> hmExtraField = new HashMap<>();
                        StringBuilder sbExtraFiled = new StringBuilder();
                       hmExtraFieldFile.forEach((k, v) -> {  // code, value
                           if (hmExtraFieldConfig.containsKey(k)) {
                               ExtraFieldObject efo = hmExtraFieldConfig.get(k);
                               hmExtraField.put(efo.getColumnName(), new LiteExtraFieldObject(efo.getColumnName(), efo.getColumnType(), v));// code, type, value
                               sbExtraFiled.append(efo.getColumnName()).append(" = ").append(v).append(", ");

                           }
                       });

                        // set group code, extra field;
                        counterObj.setGroupCode(ccco.getGroupCode());
                        counterObj.setExtraField(hmExtraField);
                        counterObj.setSExtraField(sbExtraFiled.toString());
                        // add to list
                        lstCounter.add(counterObj);
                    } else {
                        log.debug("Counter ID out of scope");
                    }

                }

            }

            return lstCounter;
        } catch (Exception ex) {
            log.error("parsing error: {}", ex.getMessage());
            return null;

        }
    }



    private HashMap<String, String> parseMeasObj(String measObjInstId) {
        HashMap<String, String> hmMeas = new HashMap<>();
        if (measObjInstId == null || measObjInstId.isEmpty()) {
            return hmMeas;
        }
        String[] arrMeas = measObjInstId.split(",");
        for (String extra : arrMeas) {
            String[] kv = extra.trim().replace("'", "\\'").split("=");
            if (kv.length != 2) {
                continue;
            }
            hmMeas.put(kv[0].toLowerCase().trim(), kv[1].trim());
        }
        return hmMeas;
    }

    @Deprecated
    private List<CounterObject> parseCounter5GAccessProtobuf(CounterDataObject preCounter) {

        return new ArrayList<>();
    }

    @Deprecated
    private List<CounterObject> parseCounter5GAccessCSV(CounterDataObject preCounter) {
        return new ArrayList<>();
    }

    public Object parseCouterClickhouse(CounterDataObject preCounterObject) {
        return null;
    }


    public List<NewFormatCounterObject> parseCounterClickHouse(ProcessDataONT preCounter) {
    return null;
    }





        public List<NewFormatCounterObject> parseCounterClickHouse(CounterDataObject preCounter) {
        List<NewFormatCounterObject> counterObject = null;
        try {
            try {
                String fileName = preCounter.getFileName();
                //gNodeB_gHI04305_1616233232_2332_4fdsfasasdf           --> Access(gNodeB)
                //A20210323.0953+2323-2332+2323_SMF_-_SMF01              --> Core (AMF, SMF, DMF, UMF...)
                if (fileName.contains("GNODEB")) {
                    if (fileName.contains("_GNODEB_") && fileName.contains("RU")) {
                        counterObject = convertOldToNewFormatCounterObject(parseCounter5GAccessCSV(preCounter));
                    } else if (fileName.contains("_GNODEB_")) {
                        counterObject = convertOldToNewFormatCounterObject(parseCounter5GAccessProtobuf(preCounter));
                    } else {
                        counterObject = convertOldToNewFormatCounterObject(parseCounter5GAccessCapnProto(preCounter));
                    }
                } else if (fileName.contains("AMF") ||
                        fileName.contains("SMF") ||
                        fileName.contains("UPF") ||
                        fileName.contains("PCF") ||
                        fileName.contains("AUSF") ||
                        fileName.contains("NSSF")
                ) {
                    counterObject = convertOldToNewFormatCounterObject(parseCounter5GCoreProtobuf(preCounter));
                } else if (
                        fileName.contains("ONT")
                ) {
                    counterObject = convertOldToNewFormatCounterObject(parseCounterONTJSON(preCounter));
                } else {
                    log.warn("⚠️ Unrecognized file format for file: {}", fileName);
                }

            } catch (Exception ex) {
                log.error("❌ Error parsing counter data from file {}: {}", preCounter.getFileName(), ex.getMessage());
                return null;
            }


            return counterObject;
        } catch (Exception ex) {
            log.error("Parsing new format error: {}", ex.getMessage());
            return null;
        }
    }


    private List<NewFormatCounterObject> convertOldToNewFormatCounterObject(ArrayList<CounterObject> counterObjectArrayList) {
        try {
            if (counterObjectArrayList == null || counterObjectArrayList.size() == 0) {
                return null;
            }
           Set<Timestamp> timestampSet = new HashSet<>();
           counterObjectArrayList.forEach(counterObj -> {
            timestampSet.add(counterObj.getTime());
           })

            List<NewFormatCounterObject> newFormatCounterObjectList = new ArrayList<>();

            for(var timestamp : timestampSet) {

                NewFormatCounterObject newFormatCounterObject = NewFormatCounterObject.builder()
                        .neId(counterObjectArrayList.get(0).getNeId())
                        .duration(counterObjectArrayList.get(0).getDuration())
                        .time(timestamp.toLocalDateTime())
                        .hmGroupCounterValues(new HashMap<>())
                        .build();

                for (CounterObject counterObject : counterObjectArrayList) {
                    newFormatCounterObject.addCounterValueObject(counterObject.getGroupCode(), counterObject.getSExtraField());
                    if (!newFormatCounterObject.getGroupCounterValueObject(counterObject.getGroupCode()).getLstCounterId().contains(counterObject.getCounterId())) {
                        newFormatCounterObject.getGroupCounterValueObject(counterObject.getGroupCode().getLstCounterId().add(counterObject.getCounterId()));
                    }
                    newFormatCounterObject.getCounterValueObject(counterObject.getGroupCode(), counterObject.getSExtraField()).setHmExtraFields(counterObject.getExtraField());
                    newFormatCounterObject.getCounterValueObject(counterObject.getGroupCode(), counterObject.getSExtraField()).getLstCounterValues().add(counterObject.getCounterValue());
                }
                newFormatCounterObjectList.add(newFormatCounterObject);

            }

           return newFormatCounterObjectList;
        return null;
        } catch (Exception ex) {
            log.error("Error while converting to new format", ex);
            return null;
        }
    }


}
