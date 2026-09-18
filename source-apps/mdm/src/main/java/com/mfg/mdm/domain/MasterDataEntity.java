package com.mfg.mdm.domain;

import java.time.LocalDateTime;

/**
 * 主数据实体的统一契约。
 *
 * <p>12 类主数据表结构各异，但都有一组共同的治理字段
 * （状态、版本、变更原因、创建人）。本接口把这组字段抽象出来，
 * 让状态机、审批接入、分发逻辑可以<b>统一处理所有主数据类型</b>，
 * 而不必为每类写一遍。
 *
 * <p>实现类示例：
 * <pre>{@code
 * @Entity
 * @Table(name = "md_customer")
 * public class Customer implements MasterDataEntity {
 *     @Column(name = "status") private String status = "DRAFT";
 *     @Column(name = "version_no") private Integer versionNo = 1;
 *     // ...
 * }
 * }</pre>
 */
public interface MasterDataEntity {

    Long getId();

    /** 业务编码：客户编码、物料编码、BOM 编码 */
    String getBusinessCode();

    /** 业务名称：客户名称、物料名称 */
    String getName();

    /** 状态字符串（与 {@link MasterDataStatus} 对应） */
    String getStatus();

    void setStatus(String status);

    /** 版本号，每次发布递增 */
    Integer getVersionNo();

    void setVersionNo(Integer versionNo);

    /** 最近一次变更原因 */
    String getChangeReason();

    void setChangeReason(String reason);

    void setUpdatedBy(String username);

    void setUpdatedAt(LocalDateTime time);

    /** 该实体对应的业务类型，用于匹配审批流程与分发目标 */
    BizType bizType();

    default MasterDataStatus statusEnum() {
        return MasterDataStatus.of(getStatus());
    }

    default boolean isPublished() {
        return statusEnum().isConsumable();
    }

    /**
     * 标记为审批中。
     *
     * <p>若当前已是 PUBLISHED（发起变更），则置为 CHANGING，
     * 旧版本在变更期间仍可被业务系统引用。
     */
    default void markPending() {
        MasterDataStatus current = statusEnum();
        setStatus(current == MasterDataStatus.PUBLISHED
                ? MasterDataStatus.CHANGING.name()
                : MasterDataStatus.PENDING.name());
        setUpdatedAt(LocalDateTime.now());
    }

    /** 审批通过 → 发布，版本号递增 */
    default void publish(String operator) {
        setStatus(MasterDataStatus.PUBLISHED.name());
        setVersionNo(getVersionNo() == null ? 1 : getVersionNo() + 1);
        setUpdatedBy(operator);
        setUpdatedAt(LocalDateTime.now());
    }

    /** 驳回 → 回到可编辑 */
    default void markRejected(String reason) {
        setStatus(MasterDataStatus.REJECTED.name());
        setChangeReason(reason);
        setUpdatedAt(LocalDateTime.now());
    }
}
