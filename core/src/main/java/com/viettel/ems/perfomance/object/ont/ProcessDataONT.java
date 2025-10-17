package com.viettel.ems.perfomance.object;

import lombok.*;

@Data
public class ProcessDataONT {
    Map<String, List<Map<String, Object>>> dataPushClickhouses;
    MeasCollectFileObject data;
    String fileName;
    String productClass;
    boolean success;
}