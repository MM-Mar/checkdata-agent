package com.checkdata.agent.sys.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 给用户分配角色入参。
 */
@Data
public class AssignRolesDTO {

    private List<Long> roleIds = new ArrayList<>();
}
