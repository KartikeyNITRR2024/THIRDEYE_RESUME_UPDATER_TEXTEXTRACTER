package com.thirdeye30.resumehelper.textextracter.dtos;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
public class AlProcesserPayload {
	private UUID resumeId;
    private ResumeMetadata resumeMetadata;
    private String content;
}