package com.thirdeye30.resumehelper.textextracter.dtos;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Message<T> {
    Timestamp timeOfComing;
    T message;
}


