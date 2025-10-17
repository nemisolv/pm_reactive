//package com.viettel.betterversion2.parser;
//
//import com.viettel.dal.CustomOutput;
//import com.viettel.dal.QueryObject;
//
//import java.util.List;
//
//public interface SQLGenerator {
//    List<QueryObject> build(BuildContext ctx);
//    SqlDialect dialect();
//
//    default CustomOutput<List<QueryObject>> wrapGeneratedSql(BuildContext ctx) {
//        List<QueryObject> lstQueryObjects = build(ctx);
//        CustomOutput<List<QueryObject>> output = new CustomOutput<>();
//        output.setFuncOutput(lstQueryObjects);
//        output.setCustomStringOutput("Total SQL Query: " + lstQueryObjects.size());
//        return output;
//    }
//}
