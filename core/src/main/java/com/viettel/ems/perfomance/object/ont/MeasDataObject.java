package com.viettel.ems.perfomance.object;

import lombok.*;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeasDataObject {
    private String measInfoName;
    private String measTypes;
    private List<MeasValueObject> measValues;
    public MeasDataObject(String measInfoName) {
        this.measInfoName = measInfoName;
    }
}