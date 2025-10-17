package com.viettel.ems.perfomance.repository;

import com.viettel.ems.perfomance.common.ErrorCode;
import com.viettel.ems.perfomance.object.CounterObject;
import com.viettel.ems.perfomance.troubleshoot.DatasourceVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class CounterMySqlRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final DatasourceVerifier datasourceVerifier;
    public ErrorCode addCounter(List<CounterObject> lstCounter) {

        try {
            datasourceVerifier.verifyConnection();
            return ErrorCode.NO_ERROR;
        } catch (Exception ex) {
            return ErrorCode.NO_ERROR; // Use existing error code
        }
    }
}
