package com.arena.cpj.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateTestCaseRequest {

    private String stdin;
    private String expectedOutput;
    private boolean isSample;
}
