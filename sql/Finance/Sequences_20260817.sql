-- ============================================================
-- 数据库序列汇总
-- 说明: 包含所有表自增ID对应的序列
-- 生成时间: 2026-08-17
-- ============================================================

-- ============================================================
-- 1. ad_user_list - AD用户列表
-- 说明: 存储Active Directory用户信息
-- ============================================================
CREATE SEQUENCE public.ad_user_list_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 2. approver_list - 审批人列表
-- 说明: 存储审批人信息
-- ============================================================
CREATE SEQUENCE public.approver_list_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 3. apv_app_cn - 应用中文信息（容灾、实施等）
-- ============================================================
CREATE SEQUENCE public.apv_app_cn_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 4. apv_app_detail - 应用详细信息（技术信息）
-- ============================================================
CREATE SEQUENCE public.apv_app_detail_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 5. apv_app_eos - 应用EOS（生命周期终止）信息
-- ============================================================
CREATE SEQUENCE public.apv_app_eos_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 6. apv_app_or2 - OR2应用标记信息
-- ============================================================
CREATE SEQUENCE public.apv_app_or2_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 7. apv_team_info - 团队信息
-- ============================================================
CREATE SEQUENCE public.apv_team_info_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 8. assetreport - 资产报告
-- ============================================================
CREATE SEQUENCE public.assetreport_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 9. domain_list - 业务域列表
-- ============================================================
CREATE SEQUENCE public.domain_list_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 10. email_templates - 邮件模板
-- ============================================================
CREATE SEQUENCE public.email_templates_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 11. end_of_support_detail - 支持终止详情
-- ============================================================
CREATE SEQUENCE public.end_of_support_detail_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 12. hardwarereport - 硬件报告
-- ============================================================
CREATE SEQUENCE public.hardwarereport_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 13. hostsoftware - 主机软件
-- ============================================================
CREATE SEQUENCE public.hostsoftware_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 14. it_representative - IT代表
-- ============================================================
CREATE SEQUENCE public.it_representative_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 15. it_tool_profile - IT工具配置
-- ============================================================
CREATE SEQUENCE public.it_tool_profile_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 16. new_application_registration_request - 新应用注册请求
-- ============================================================
CREATE SEQUENCE public.new_application_registration_request_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 17. services_catalogue - 服务目录
-- ============================================================
CREATE SEQUENCE public.services_catalogue_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 18. softwareeos - 软件EOS（生命周期终止）
-- ============================================================
CREATE SEQUENCE public.softwareeos_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 19. system_dict_data - 系统字典数据
-- ============================================================
CREATE SEQUENCE public.system_dict_data_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 20. t_auth_permissions - 权限表
-- ============================================================
CREATE SEQUENCE public.t_auth_permissions_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 21. t_auth_roles - 角色表
-- ============================================================
CREATE SEQUENCE public.t_auth_roles_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 22. t_auth_token_sessions - Token会话表
-- ============================================================
CREATE SEQUENCE public.t_auth_token_sessions_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 23. t_auth_users - 用户表
-- ============================================================
CREATE SEQUENCE public.t_auth_users_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 24. t_file - 文件表
-- ============================================================
CREATE SEQUENCE public.t_file_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 25. update_app_details - 更新应用详情
-- ============================================================
CREATE SEQUENCE public.update_app_details_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 26. update_domain_list - 更新域列表
-- ============================================================
CREATE SEQUENCE public.update_domain_list_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 27. update_dropdown_value - 更新下拉值
-- ============================================================
CREATE SEQUENCE public.update_dropdown_value_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 28. update_end_of_support_detail - 更新支持终止详情
-- ============================================================
CREATE SEQUENCE public.update_end_of_support_detail_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 29. update_equipment_evaluation_representative - 更新设备评估代表
-- ============================================================
CREATE SEQUENCE public.update_equipment_evaluation_representative_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 30. update_it_rep_request - 更新IT代表请求
-- ============================================================
CREATE SEQUENCE public.update_it_rep_request_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 31. update_it_representative - 更新IT代表
-- ============================================================
CREATE SEQUENCE public.update_it_representative_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 32. update_team_head - 更新团队负责人
-- ============================================================
CREATE SEQUENCE public.update_team_head_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 33. z_config - 系统配置表
-- ============================================================
CREATE SEQUENCE public.z_config_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- ============================================================
-- 序列授权（可选，根据实际需要启用）
-- ============================================================
-- GRANT USAGE ON SEQUENCE public.ad_user_list_id_seq TO your_user;
-- GRANT USAGE ON SEQUENCE public.approver_list_id_seq TO your_user;
-- ... 其他序列类似