package com.thirdeye30.resumehelper.textextracter.dtos;

import java.time.LocalDateTime;
import java.util.*;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResumeMailPayload {
    private String name;
    private String email;
    private Map<String, String> resumeUrls;
    private String jobUrl;
    private LocalDateTime creationTime;
}
