#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
对比后端实体类与数据库表结构差异
输出：实体类有但数据库缺少的字段 + 数据库有但实体类缺少的字段

用法：
    cd /Users/dawei/Documents/ddup/ai/flow
    python3 compare_entity_db.py
"""

import os
import re
import subprocess
import sys

ENTITY_DIR = "workflow-server/src/main/java/com/workflow/entity"
OUTPUT_FILE = "entity_db_diff.txt"
DB_NAME = "workflow"
DB_USER = "root"
DB_PASS = "zhoudawei"


def parse_entity_file(path):
    """解析实体类文件，提取 {字段名: 属性名} 和表名"""
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 提取表名
    table_match = re.search(r'@TableName\("([^"]+)"\)', content)
    if not table_match:
        return None, None
    table_name = table_match.group(1)

    # 提取字段映射
    fields = {}  # {数据库列名: 实体属性名}

    # 匹配 @TableField("xxx") 或 @TableField(value = "xxx") 注解后的字段
    # 同时处理带 fill 的情况
    pattern = re.compile(
        r'@TableField\((?:value\s*=\s*)?"([^"]+)".*?\)\s*\n\s*private\s+\S+\s+(\w+);',
        re.DOTALL
    )
    for m in pattern.finditer(content):
        db_col = m.group(1)
        attr_name = m.group(2)
        fields[db_col] = attr_name

    # 匹配没有 @TableField 注解的 private 字段（默认驼峰转下划线）
    # 但要排除 @TableField(exist = false)、@TableId、@TableLogic 的字段
    lines = content.split('\n')
    skip_next = False
    for i, line in enumerate(lines):
        stripped = line.strip()
        # 如果遇到跳过标记，设置标志
        if '@TableField(exist = false)' in stripped or \
           '@TableId' in stripped or \
           '@TableLogic' in stripped:
            skip_next = True
            continue
        # 如果遇到 @TableField 注解，说明已在上面的正则处理过，跳过下一行
        if '@TableField' in stripped:
            skip_next = True
            continue
        # 匹配 private 字段声明
        m = re.match(r'private\s+\S+\s+(\w+);', stripped)
        if m and not skip_next:
            attr_name = m.group(1)
            # 驼峰转下划线
            db_col = camel_to_underscore(attr_name)
            if db_col not in fields:
                fields[db_col] = attr_name
        skip_next = False

    return table_name, fields


def camel_to_underscore(name):
    """驼峰命名转下划线命名"""
    s = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s).lower()


def get_db_all_columns():
    """获取数据库中所有表的列信息"""
    sql = f"""
    SELECT TABLE_NAME, COLUMN_NAME
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = '{DB_NAME}'
    ORDER BY TABLE_NAME, ORDINAL_POSITION;
    """
    cmd = ['mysql', '-u', DB_USER, f'-p{DB_PASS}', '-e', sql.strip()]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print("数据库查询失败:", result.stderr, file=sys.stderr)
        sys.exit(1)

    db_cols = {}
    for line in result.stdout.strip().split('\n')[1:]:
        parts = line.split('\t')
        if len(parts) >= 2:
            table, col = parts[0], parts[1]
            db_cols.setdefault(table, set()).add(col)
    return db_cols


def main():
    print("正在解析实体类...")
    entity_tables = {}  # {表名: {数据库列名: 属性名}}

    entity_dir = os.path.join(os.path.dirname(__file__), ENTITY_DIR)
    for fname in sorted(os.listdir(entity_dir)):
        if not fname.endswith('.java'):
            continue
        path = os.path.join(entity_dir, fname)
        table_name, fields = parse_entity_file(path)
        if table_name and fields:
            entity_tables[table_name] = fields

    print(f"  发现 {len(entity_tables)} 个实体类")

    print("正在查询数据库结构...")
    db_cols = get_db_all_columns()
    print(f"  数据库中共有 {len(db_cols)} 张表")

    print("正在对比差异...")
    lines = [
        "=" * 80,
        "实体类 vs 数据库 字段差异对比报告",
        "=" * 80,
        "",
        "说明：",
        "  [实体有，DB无] = 实体类定义了但数据库表缺少的字段",
        "  [DB有，实体无] = 数据库表存在但实体类没映射的字段",
        "",
    ]

    # 对比所有实体类对应的表
    for table_name in sorted(entity_tables.keys()):
        entity_fields = entity_tables[table_name]
        db_fields = db_cols.get(table_name, set())

        if not db_fields:
            lines.append(f"\n{'='*80}")
            lines.append(f"表: {table_name}")
            lines.append("  ⚠️ 数据库中不存在此表！")
            lines.append(f"  实体类字段: {', '.join(sorted(entity_fields.keys()))}")
            continue

        # 实体有但DB无
        only_in_entity = []
        for db_col, attr_name in sorted(entity_fields.items()):
            if db_col not in db_fields:
                only_in_entity.append(f"    - {db_col} (属性: {attr_name})")

        # DB有但实体无
        only_in_db = []
        for db_col in sorted(db_fields):
            if db_col not in entity_fields:
                only_in_db.append(f"    - {db_col}")

        if only_in_entity or only_in_db:
            lines.append(f"\n{'='*80}")
            lines.append(f"表: {table_name}")
            lines.append(f"  实体类字段数: {len(entity_fields)}")
            lines.append(f"  数据库字段数: {len(db_fields)}")

            if only_in_entity:
                lines.append(f"\n  [实体有，DB无] - {len(only_in_entity)} 个:")
                lines.extend(only_in_entity)

            if only_in_db:
                lines.append(f"\n  [DB有，实体无] - {len(only_in_db)} 个:")
                lines.extend(only_in_db)

    # 统计
    total_entity_only = 0
    total_db_only = 0
    diff_tables = 0
    for table_name, entity_fields in entity_tables.items():
        db_fields = db_cols.get(table_name, set())
        eo = sum(1 for c in entity_fields if c not in db_fields)
        dob = sum(1 for c in db_fields if c not in entity_fields)
        if eo or dob:
            total_entity_only += eo
            total_db_only += dob
            diff_tables += 1

    lines.append(f"\n{'='*80}")
    lines.append("汇总")
    lines.append(f"{'='*80}")
    lines.append(f"有差异的表: {diff_tables} 张")
    lines.append(f"实体有但DB无的字段: {total_entity_only} 个")
    lines.append(f"DB有但实体无的字段: {total_db_only} 个")
    lines.append("")

    report = '\n'.join(lines)
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        f.write(report)

    print(report)
    print(f"\n✅ 报告已保存到 {OUTPUT_FILE}")


if __name__ == '__main__':
    main()
