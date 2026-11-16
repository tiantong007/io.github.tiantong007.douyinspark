package com.worldmargin.autospark.app.config.model

/** 同步策略：父模板改动后，向子模板推送的方式。 */
enum class SyncMode {
    /** 仅同步「未修改」的子模板（保留用户改过的）。 */
    UNMODIFIED,

    /** 仅同步「已修改」的子模板（用父覆盖本地改动）。 */
    MODIFIED,

    /** 全部同步。 */
    ALL,
}

/** 通用模板（可命名，多个会话可挂在其下共享同一份文案）。 */
/** 模板变量：名字 + 一条 JS 表达式（求值时可用 ctx，按定义顺序求值）。 */
