package com.viettel.ems.perfomance.repository;

import com.viettel.ems.perfomance.object.FTPPathObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
@RequiredArgsConstructor
public class FTPPathRepository {
    private final JdbcTemplate jdbcTemplate;
    public List<FTPPathObject> findAll() {
        String sql = "SELECT host, port, username, password, path from ftp_server JOIN ftp_path ON ftp_server.id = ftp_path.ftp_server_id ";
        return jdbcTemplate.query(sql, (rs, i) -> FTPPathObject.fromRs(rs));
    }
}
