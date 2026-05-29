package com.thirdeye30.resumehelper.textextracter.dtos;

import java.util.UUID;

import lombok.Data;

@Data
public class ResumeMetadata{
	private UUID userId;
	private String jobDescription;
	private String jobTitle;   
	private Integer yearsOfExperience;
}
