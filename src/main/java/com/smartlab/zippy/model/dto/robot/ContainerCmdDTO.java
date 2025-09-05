package com.smartlab.zippy.model.dto.robot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerCmdDTO {
    private int lock; // 0=open, 1=true
}
