//package com.viettel.controller;
//
//
//import com.google.gson.Gson;
//import jakarta.servlet.http.HttpServletResponse;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequiredArgsConstructor
//@RequestMapping("/exp_raw")
//@Slf4j
//public class EpxRawController {
//    private final ExpRawService exprawService;
//
//    @PostMapping("/get_data")
//    public void getRawData(@Valid @RequestBody HttpReqExpRaw input, HttpServletResponse response) {
//        log.info("export data input request: {}", new Gson().toJson(input));
//        try {
//
//        }catch(Exception ex) {
//            log.error("error message /get_data: {}", ex.getMessage());
//
//        }
//    }
//}
