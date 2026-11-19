DROP TABLE IF EXISTS `agentx_session`;
CREATE TABLE `agentx_session` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `conversation_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话窗口ID',
  `session_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '本次调用ID',
  `state_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态键: original_messages / working_messages / offload_context',
  `item_index` int NOT NULL DEFAULT 0 COMMENT '消息在状态键内的序号',
  `state_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息JSON（MessageJsonSerializer 序列化）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_state`(`session_id` ASC, `state_key` ASC) USING BTREE,
  INDEX `idx_conv_state`(`conversation_id` ASC, `state_key` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AgentX会话消息链表' ROW_FORMAT = Dynamic;