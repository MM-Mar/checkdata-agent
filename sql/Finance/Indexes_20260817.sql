-- ============================================================
-- 数据库索引汇总
-- 说明: 包含所有表的主键索引和唯一约束索引
-- 生成时间: 2026-08-17
-- ============================================================

-- ============================================================
-- 1. ad_user_list - AD用户列表
-- 说明: 存储Active Directory用户信息
-- ============================================================
CREATE UNIQUE INDEX ad_user_list_pkey ON public.ad_user_list USING btree (id);

-- ============================================================
-- 2. approver_list - 审批人列表
-- 说明: 存储审批人信息
-- ============================================================
CREATE UNIQUE INDEX approver_list_pkey ON public.approver_list USING btree (id);

-- ============================================================
-- 3. apv_app_cn - 应用中文信息（容灾、实施等）
-- ============================================================
CREATE UNIQUE INDEX apv_app_cn_pkey ON public.apv_app_cn USING btree (id);

-- ============================================================
-- 4. apv_app_detail - 应用详细信息（技术信息）
-- ============================================================
CREATE UNIQUE INDEX apv_app_detail_pkey ON public.apv_app_detail USING btree (id);

-- ============================================================
-- 5. apv_app_eos - 应用EOS（生命周期终止）信息
-- ============================================================
CREATE UNIQUE INDEX apv_app_eos_pkey ON public.apv_app_eos USING btree (id);

-- ============================================================
-- 6. apv_app_or2 - OR2应用标记信息
-- ============================================================
CREATE UNIQUE INDEX apv_app_or2_pkey ON public.apv_app_or2 USING btree (id);

-- ============================================================
-- 7. apv_reserved_app_code - 保留应用代码
-- 说明: 主键为 app_code
-- ============================================================
CREATE UNIQUE INDEX apv_reserved_app_code_pkey ON public.apv_reserved_app_code USING btree (app_code);

-- ============================================================
-- 8. apv_team_info - 团队信息
-- ============================================================
CREATE UNIQUE INDEX apv_team_info_pkey ON public.apv_team_info USING btree (id);

-- ============================================================
-- 9. assetreport - 资产报告
-- ============================================================
CREATE UNIQUE INDEX assetreport_pkey ON public.assetreport USING btree (id);

-- ============================================================
-- 10. domain_list - 业务域列表
-- ============================================================
CREATE UNIQUE INDEX domain_list_pkey ON public.domain_list USING btree (id);

-- ============================================================
-- 11. email_templates - 邮件模板
-- ============================================================
CREATE UNIQUE INDEX email_templates_pkey ON public.email_templates USING btree (id);

-- ============================================================
-- 12. end_of_support_detail - 支持终止详情
-- ============================================================
CREATE UNIQUE INDEX end_of_support_detail_pkey ON public.end_of_support_detail USING btree (id);

-- ============================================================
-- 13. hardwarereport - 硬件报告
-- ============================================================
CREATE UNIQUE INDEX hardwarereport_pkey ON public.hardwarereport USING btree (id);

-- ============================================================
-- 14. hostsoftware - 主机软件
-- ============================================================
CREATE UNIQUE INDEX hostsoftware_pkey ON public.hostsoftware USING btree (id);

-- ============================================================
-- 15. it_representative - IT代表
-- ============================================================
CREATE UNIQUE INDEX it_representative_pkey ON public.it_representative USING btree (id);

-- ============================================================
-- 16. it_tool_profile - IT工具配置
-- ============================================================
CREATE UNIQUE INDEX it_tool_profile_pkey ON public.it_tool_profile USING btree (id);

-- ============================================================
-- 17. new_application_registration_request - 新应用注册请求
-- ============================================================
CREATE UNIQUE INDEX new_application_registration_request_pkey ON public.new_application_registration_request USING btree (id);

-- ============================================================
-- 18. services_catalogue - 服务目录
-- ============================================================
CREATE UNIQUE INDEX services_catalogue_pkey ON public.services_catalogue USING btree (id);

-- ============================================================
-- 19. softwareeos - 软件EOS（生命周期终止）
-- ============================================================
CREATE UNIQUE INDEX softwareeos_pkey ON public.softwareeos USING btree (id);

-- ============================================================
-- 20. system_dict_data - 系统字典数据
-- ============================================================
CREATE UNIQUE INDEX system_dict_data_pkey ON public.system_dict_data USING btree (id);

-- ============================================================
-- 21. t_auth_permissions - 权限表
-- ============================================================
CREATE UNIQUE INDEX t_auth_permissions_pkey ON public.t_auth_permissions USING btree (id);

-- 权限代码唯一索引
CREATE UNIQUE INDEX ukgigt0988f9gb35u5df5inh50 ON public.t_auth_permissions USING btree (code);

-- ============================================================
-- 22. t_auth_roles - 角色表
-- ============================================================
CREATE UNIQUE INDEX t_auth_roles_pkey ON public.t_auth_roles USING btree (id);

-- 角色名称唯一索引
CREATE UNIQUE INDEX uk1589abjt2unxooupn6j1te7h ON public.t_auth_roles USING btree (name);

-- ============================================================
-- 23. t_auth_token_sessions - Token会话表
-- ============================================================
CREATE UNIQUE INDEX t_auth_token_sessions_pkey ON public.t_auth_token_sessions USING btree (id);

-- Refresh Token JTI唯一索引
CREATE UNIQUE INDEX uk6gmp1hkg9ui5pe8bml1wg066r ON public.t_auth_token_sessions USING btree (refresh_token_jti);

-- Access Token JTI唯一索引
CREATE UNIQUE INDEX ukgd5fga2f78ox9mpnp5v1q815u ON public.t_auth_token_sessions USING btree (access_token_jti);

-- ============================================================
-- 24. t_auth_user_roles - 用户角色关联表
-- 说明: 复合主键 (user_id, role_id)
-- ============================================================
CREATE UNIQUE INDEX t_auth_user_roles_pkey ON public.t_auth_user_roles USING btree (user_id, role_id);

-- ============================================================
-- 25. t_auth_users - 用户表
-- ============================================================
CREATE UNIQUE INDEX t_auth_users_pkey ON public.t_auth_users USING btree (id);

-- 用户名唯一索引
CREATE UNIQUE INDEX uk8uliw85pqsyfhwt2k3y8uc764 ON public.t_auth_users USING btree (username);

-- ============================================================
-- 26. t_file - 文件表
-- ============================================================
CREATE UNIQUE INDEX t_file_pkey ON public.t_file USING btree (id);

-- ============================================================
-- 27. update_app_details - 更新应用详情
-- ============================================================
CREATE UNIQUE INDEX update_app_details_pkey ON public.update_app_details USING btree (id);

-- ============================================================
-- 28. update_domain_list - 更新域列表
-- ============================================================
CREATE UNIQUE INDEX update_domain_list_pkey ON public.update_domain_list USING btree (id);

-- ============================================================
-- 29. update_dropdown_value - 更新下拉值
-- ============================================================
CREATE UNIQUE INDEX update_dropdown_value_pkey ON public.update_dropdown_value USING btree (id);

-- ============================================================
-- 30. update_end_of_support_detail - 更新支持终止详情
-- ============================================================
CREATE UNIQUE INDEX update_end_of_support_detail_pkey ON public.update_end_of_support_detail USING btree (id);

-- ============================================================
-- 31. update_equipment_evaluation_representative - 更新设备评估代表
-- ============================================================
CREATE UNIQUE INDEX update_equipment_evaluation_representative_pkey ON public.update_equipment_evaluation_representative USING btree (id);

-- ============================================================
-- 32. update_it_rep_request - 更新IT代表请求
-- ============================================================
CREATE UNIQUE INDEX update_it_rep_request_pkey ON public.update_it_rep_request USING btree (id);

-- ============================================================
-- 33. update_it_representative - 更新IT代表
-- ============================================================
CREATE UNIQUE INDEX update_it_representative_pkey ON public.update_it_representative USING btree (id);

-- ============================================================
-- 34. update_team_head - 更新团队负责人
-- ============================================================
CREATE UNIQUE INDEX update_team_head_pkey ON public.update_team_head USING btree (id);

-- ============================================================
-- 35. z_config - 系统配置表
-- ============================================================
CREATE UNIQUE INDEX z_config_pkey ON public.z_config USING btree (id);