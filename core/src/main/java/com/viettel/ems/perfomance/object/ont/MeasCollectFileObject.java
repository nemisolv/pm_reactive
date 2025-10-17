
package com.viettel.ems.perfomance.object;

import lombok.*;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeasCollectFileObject {
    private String userLabel;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private int duration;
    private List<MeasDataObject> measData;
}