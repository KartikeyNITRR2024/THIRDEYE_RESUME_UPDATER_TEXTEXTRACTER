package com.thirdeye30.resumehelper.textextracter.dtos;
import java.util.UUID;

import lombok.Data;
import lombok.ToString;
@Data
@ToString
public class TextExtracterPayload {
	private UUID resumeId;
	private UUID awsPath;
	private String encryptionkey;
	private ResumeMetadata resumeMetadata;
}
