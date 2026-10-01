package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PositionDto {
    private Long id;
    private String code;
    private String name;
}
