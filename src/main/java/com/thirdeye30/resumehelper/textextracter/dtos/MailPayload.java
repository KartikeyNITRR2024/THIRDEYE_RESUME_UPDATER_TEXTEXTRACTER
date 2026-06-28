package com.thirdeye30.resumehelper.textextracter.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.thirdeye30.resumehelper.textextracter.enums.MailType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MailPayload {
	private UUID id;
	private MailType mailType;
    private String email;
    private String name;
    private Map<String, String> resumeUrls;
    private String jobUrl;
    private String oldResumeUrl;
    private LocalDateTime creationTime;
	private String courseUrl;
	private List<String> highPriority;
	private List<String> mediumPriority;
	private List<String> lowPriority;
	private String company;

}
