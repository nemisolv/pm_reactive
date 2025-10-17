package com.viettel.ems.perfomance.object;

import lombok.*;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeasValuesObject  {
    private String measObjLdn;
    private String measresults;
    
    public MeasValuesObject(String measObjLdn) {this.measObjLdn = measObjLdn;}
}