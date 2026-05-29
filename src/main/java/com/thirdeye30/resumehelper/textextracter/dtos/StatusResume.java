package com.thirdeye30.resumehelper.textextracter.dtos;

import java.util.UUID;

import com.thirdeye30.resumehelper.textextracter.enums.Status;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class StatusResume {
	private UUID resumeId;
	private Status status;
}
