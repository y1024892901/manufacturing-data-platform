package com.mfg.security.scope;

import lombok.Builder;
import lombok.Getter;

/**
 * 本次请求的数据范围上下文。
 *
 * <p>由 {@link DataScopeAspect} 在方法执行前设置、执行后清理，
 * Repository / Service 通过 {@link #current()} 读取并自行拼装查询条件。
 *
 * <p>为什么用 ThreadLocal 而不是方法参数：
 * 数据范围是横切关注点，若作为参数层层传递会污染所有方法签名；
 * 且调用链中间层并不关心它，只有最终拼 SQL 的那一层需要。
 */
@Getter
@Builder
public final class DataScopeContext {

    /** 范围类型：ALL / DEPT / SELF */
    private final ScopeType type;

    private final String resource;

    /** SELF 范围下匹配的用户标识（用户名） */
    private final String username;

    /** DEPT 范围下匹配的部门编码 */
    private final String deptCode;

    /** 用户维度字段名 */
    private final String userField;

    /** 部门维度字段名 */
    private final String deptField;

    public enum ScopeType {
        /** 不受限 */
        ALL,
        /** 本部门 */
        DEPT,
        /** 仅本人 */
        SELF,
        /** 无任何可见数据（用于异常兜底，宁可不显示也不越权） */
        NONE
    }

    public boolean isUnrestricted() {
        return type == ScopeType.ALL;
    }

    public boolean isDenied() {
        return type == ScopeType.NONE;
    }

    // ---------- ThreadLocal 管理 ----------

    private static final ThreadLocal<DataScopeContext> HOLDER = new ThreadLocal<>();

    public static void set(DataScopeContext ctx) {
        HOLDER.set(ctx);
    }

    /** 当前上下文；无上下文时返回「不受限」，便于定时任务等场景 */
    public static DataScopeContext current() {
        DataScopeContext ctx = HOLDER.get();
        return ctx != null ? ctx : DataScopeContext.builder()
                .type(ScopeType.ALL)
                .build();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
