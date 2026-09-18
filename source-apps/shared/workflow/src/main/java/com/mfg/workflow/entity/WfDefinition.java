package com.mfg.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 审批流程定义（mfg_auth.wf_definition）。
 *
 * <p>7 条审批链各是一条定义，节点在 {@link WfNode} 中按 {@code nodeSeq} 顺序排列。
 * 新增审批流只需插数据，不改代码。
 */
@Entity
@Table(name = "wf_definition")
@Getter
@Setter
@NoArgsConstructor
public class WfDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 流程编码，如 MDM_BOM_CHANGE */
    @Column(name = "def_code", nullable = false, length = 32)
    private String defCode;

    @Column(name = "def_name", nullable = false, length = 100)
    private String defName;

    /** 业务类型：CUSTOMER / SUPPLIER / MATERIAL / BOM / ROUTING / PROD_ORDER */
    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    @Column(name = "def_version", nullable = false)
    private Integer defVersion = 1;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "definitionId", fetch = FetchType.EAGER)
    @OrderBy("nodeSeq ASC")
    private List<WfNode> nodes = new ArrayList<>();

    /** 最后一个节点的顺序号，用于判断流程是否走完 */
    public Integer lastNodeSeq() {
        return nodes.isEmpty() ? null : nodes.get(nodes.size() - 1).getNodeSeq();
    }

    /** 按顺序号找节点 */
    public WfNode nodeAt(int seq) {
        return nodes.stream()
                .filter(n -> n.getNodeSeq() == seq)
                .findFirst()
                .orElse(null);
    }

    /** 找下一个节点；没有则返回 null（表示流程结束） */
    public WfNode nextNode(int currentSeq) {
        return nodes.stream()
                .filter(n -> n.getNodeSeq() > currentSeq)
                .min(java.util.Comparator.comparingInt(WfNode::getNodeSeq))
                .orElse(null);
    }
}
