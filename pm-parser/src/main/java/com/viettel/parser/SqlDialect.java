//package com.viettel.betterversion2.parser;
//
//
//import lombok.Getter;
//
//@Getter
//public enum SqlDialect {
//    CLICKHOUSE("clickhouse"),MARIADB("mariadb");
//    private final String code;
//
//     SqlDialect(String code) {
//        this.code = code;
//    }
//
//    public static SqlDialect fromValue(String value) {
//         for( var dialect : SqlDialect.values()) {
//             if(dialect.code.equalsIgnoreCase(value)) {
//                 return dialect;
//             }
//         }
//         return null;
//    }
//
//}
