CREATE TABLE IF NOT EXISTS src_erp.erp_md_product (
    product_code VARCHAR(32) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    product_model VARCHAR(100) NULL,
    lifecycle_status VARCHAR(16) NULL,
    master_version INT NOT NULL DEFAULT 1,
    synced_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS src_plm.plm_md_routing (
    routing_code VARCHAR(32) NOT NULL,
    routing_version VARCHAR(16) NOT NULL,
    routing_name VARCHAR(200) NOT NULL,
    product_code VARCHAR(32) NOT NULL,
    effective_date DATE NOT NULL,
    master_version INT NOT NULL DEFAULT 1,
    synced_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (routing_code, routing_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS src_plm.plm_md_routing_operation (
    routing_code VARCHAR(32) NOT NULL,
    routing_version VARCHAR(16) NOT NULL,
    op_seq INT NOT NULL,
    operation_code VARCHAR(32) NOT NULL,
    operation_name VARCHAR(100) NOT NULL,
    work_center VARCHAR(50) NULL,
    setup_time_min DECIMAL(10,2) NOT NULL DEFAULT 0,
    run_time_min DECIMAL(10,4) NOT NULL DEFAULT 0,
    default_equipment_code VARCHAR(32) NULL,
    is_key_operation TINYINT(1) NOT NULL DEFAULT 0,
    is_inspection_op TINYINT(1) NOT NULL DEFAULT 0,
    master_version INT NOT NULL DEFAULT 1,
    synced_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (routing_code, routing_version, op_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
