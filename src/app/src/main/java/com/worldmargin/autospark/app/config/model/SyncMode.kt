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
