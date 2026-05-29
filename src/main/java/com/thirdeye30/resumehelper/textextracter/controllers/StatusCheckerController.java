package com.thirdeye30.resumehelper.textextracter.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statuschecker")
public class StatusCheckerController {

    private static final Logger logger = LoggerFactory.getLogger(StatusCheckerController.class);

    @Value("${thirdeye.uniqueCode}")
    private String uniqueCode;

    @GetMapping("/{code}")
    public ResponseEntity<String> getStatus( @PathVariable("code") String code) {
        if (code.equals(uniqueCode)) {
            return ResponseEntity.ok("Valid credentials");
        } else {
            logger.warn("Status check failed for code: {}", code);
            return ResponseEntity.status(404).body("Invalid credentials");
        }
    }
}
