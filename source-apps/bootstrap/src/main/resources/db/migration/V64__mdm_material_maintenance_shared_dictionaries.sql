-- MDM material maintenance starts with a shared set of usable units and categories.
-- Every business module reads these central rows through /api/mdm/options.
INSERT IGNORE INTO src_mdm.md_unit
    (unit_code, unit_name, unit_type, base_unit_code, convert_rate, status, version_no, created_by, updated_by)
VALUES
    ('PCS', '件', 'QUANTITY', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('EA', '个', 'QUANTITY', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('SET', '套', 'QUANTITY', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('BOX', '箱', 'QUANTITY', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('KG', '千克', 'WEIGHT', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('G', '克', 'WEIGHT', 'KG', 0.001, 'PUBLISHED', 1, 'system', 'system'),
    ('TON', '吨', 'WEIGHT', 'KG', 1000, 'PUBLISHED', 1, 'system', 'system'),
    ('M', '米', 'LENGTH', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('CM', '厘米', 'LENGTH', 'M', 0.01, 'PUBLISHED', 1, 'system', 'system'),
    ('MM', '毫米', 'LENGTH', 'M', 0.001, 'PUBLISHED', 1, 'system', 'system'),
    ('M2', '平方米', 'AREA', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('L', '升', 'VOLUME', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('ML', '毫升', 'VOLUME', 'L', 0.001, 'PUBLISHED', 1, 'system', 'system'),
    ('M3', '立方米', 'VOLUME', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('MIN', '分钟', 'TIME', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('HOUR', '小时', 'TIME', 'MIN', 60, 'PUBLISHED', 1, 'system', 'system'),
    ('DAY', '天', 'TIME', 'MIN', 1440, 'PUBLISHED', 1, 'system', 'system'),
    ('KWH', '千瓦时', 'ENERGY', NULL, 1, 'PUBLISHED', 1, 'system', 'system'),
    ('MWH', '兆瓦时', 'ENERGY', 'KWH', 1000, 'PUBLISHED', 1, 'system', 'system'),
    ('GJ', '吉焦', 'ENERGY', 'KWH', 277.777778, 'PUBLISHED', 1, 'system', 'system');

INSERT IGNORE INTO src_mdm.md_material_category
    (category_code, category_name, parent_id, category_level, is_leaf, category_path, status, version_no, created_by, updated_by)
VALUES
    ('RAW-MATERIAL', '原材料', NULL, 1, 1, '原材料', 'PUBLISHED', 1, 'system', 'system'),
    ('SEMI-FINISHED', '半成品', NULL, 1, 1, '半成品', 'PUBLISHED', 1, 'system', 'system'),
    ('FINISHED-GOODS', '成品', NULL, 1, 1, '成品', 'PUBLISHED', 1, 'system', 'system'),
    ('SPARE-PART', '备品备件', NULL, 1, 1, '备品备件', 'PUBLISHED', 1, 'system', 'system'),
    ('PACKAGING', '包装材料', NULL, 1, 1, '包装材料', 'PUBLISHED', 1, 'system', 'system');
