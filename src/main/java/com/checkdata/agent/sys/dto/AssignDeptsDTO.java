package com.checkdata.agent.sys.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 给用户分配部门入参。
 */
@Data
public class AssignDeptsDTO {

    private List<Long> deptIds = new ArrayList<>();
}
