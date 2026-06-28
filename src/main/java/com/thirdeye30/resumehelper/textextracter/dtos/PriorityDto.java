package com.thirdeye30.resumehelper.textextracter.dtos;
import java.util.List;
import java.util.UUID;

import com.thirdeye30.resumehelper.textextracter.enums.Status;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PriorityDto {
	private UUID id;
	private Status status;
}
