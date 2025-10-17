package com.viettel.ems.perfomance.object;

import lombok.Data;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CounterInfoObject {
    private FtpServerObject ftpServerObject;
    private String path;
    private String fileName;
}
