//package com.viettel.betterversion2.parser;
//
//import com.viettel.dal.AllInputKpiCounterObjFlatten;
//import com.viettel.dal.ExtraFieldObject;
//import com.viettel.dal.InputStatisticData;
//import com.viettel.entity.Counter;
//import com.viettel.entity.CounterCat;
//import com.viettel.entity.GroupCounterCounter;
//import com.viettel.entity.KpiFormula;
//import lombok.Builder;
//import lombok.Value;
//
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//
//@Value
//@Builder
//public class BuildContext {
//  InputStatisticData inputData;
//  AllInputKpiCounterObjFlatten allInputKpiCounterObjFlatten;
//  List<CounterCat> lstAllCounterCat;
//  List<GroupCounterCounter> lstAllGroupCounterCounter;
//  LinkedHashMap<String, ExtraFieldObject> hmExtraField;      // keep LinkedHashMap for deterministic order
//  HashMap<String, ExtraFieldObject> hmKeyField;                  // narrow to Map if order not required
//  SqlDialect dialect;                                        // <-- use enum instead of String dbType
//}
