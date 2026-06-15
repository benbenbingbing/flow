#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
对比 V001__business_schema.sql 与数据库实际结构，
生成 V001 中定义但数据库中缺失的字段/表的 DDL 脚本。

用法：
    cd /Users/dawei/Documents/ddup/ai/flow
    python3 compare_schema.py

输出文件：schema_diff.sql
"""

import re
import subprocess
import sys

SQL_FILE = "workflow-server/src/main/resources/db/migration/V001__business_schema.sql"
OUTPUT_FILE = "schema_diff.sql"
DB_NAME = "workflow"
DB_USER = "root"
DB_PASS = "zhoudawei"


def parse_sql_schema(path):
    """解析 SQL 文件，提取 {表名: {字段名: 字段定义行}}"""
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 去掉注释行
    lines = []
    for line in content.splitlines():
        stripped = line.strip()
        if stripped.startswith('--') or stripped.startswith('/*!'):
            continue
        lines.append(line)

    clean_content = '\n'.join(lines)

    # 提取 CREATE TABLE 块
    tables = {}
    pattern = re.compile(
        r'CREATE\s+TABLE\s+`(?P<table>\w+)`\s*\((?P<body>.*?)\)\s*ENGINE=',
        re.DOTALL | re.IGNORECASE
    )

    for m in pattern.finditer(clean_content):
        table_name = m.group('table')
        body = m.group('body')
        fields = {}

        for line in body.split(','):
            line = line.strip()
            # 跳过约束定义行（PRIMARY KEY, UNIQUE KEY, KEY, INDEX, CONSTRAINT）
            if not line or line.startswith('PRIMARY KEY') or \
               line.startswith('UNIQUE KEY') or line.startswith('KEY ') or \
               line.startswith('INDEX') or line.startswith('CONSTRAINT'):
                continue
            # 匹配字段定义：`column_name` type ...
            fm = re.match(r'`(\w+)`\s+(.+)', line)
            if fm:
                col_name = fm.group(1)
                col_def = fm.group(2).strip()
                # 去掉行尾注释中的逗号（如果有）
                col_def = re.sub(r',\s*$', '', col_def)
                # 列名映射：V001 中的 created_at/updated_at 对应数据库中的 create_time/update_time
                col_name = COLUMN_NAME_MAP.get(col_name, col_name)
                fields[col_name] = col_def

        tables[table_name] = fields

    return tables


# 列名映射：V001 中的 created_at/updated_at 对应数据库中的 create_time/update_time
COLUMN_NAME_MAP = {
    'created_at': 'create_time',
    'updated_at': 'update_time',
}


def get_db_columns():
    """查询数据库中所有表的列名"""
    sql = f"""
    SELECT TABLE_NAME, COLUMN_NAME
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = '{DB_NAME}'
    ORDER BY TABLE_NAME, ORDINAL_POSITION;
    """
    cmd = [
        'mysql', '-u', DB_USER, f'-p{DB_PASS}',
        '-e', sql.strip()
    ]
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


def generate_diff_ddl(schema, db_cols):
    """生成差异 DDL"""
    lines = [
        "-- 由 compare_schema.py 自动生成",
        "-- 对比 V001__business_schema.sql 与数据库实际结构",
        "-- 仅包含 V001 中定义但数据库中缺失的字段/表",
        "",
        f"USE `{DB_NAME}`;",
        "",
    ]

    for table_name, fields in schema.items():
        if table_name not in db_cols:
            # 整张表缺失：输出 CREATE TABLE（从原 SQL 中提取）
            lines.append(f"-- 表 {table_name} 不存在，需要创建")
            # 这里只给出提示，因为完整 CREATE TABLE 建议从 V001 复制
            lines.append(f"-- 请从 V001__business_schema.sql 复制 CREATE TABLE `{table_name}` 语句")
            lines.append("")
            continue

        existing_cols = db_cols[table_name]
        missing = []
        for col_name, col_def in fields.items():
            if col_name not in existing_cols:
                missing.append((col_name, col_def))

        if missing:
            lines.append(f"-- 表 {table_name} 缺失字段：")
            for col_name, col_def in missing:
                # 构造 ALTER TABLE ADD COLUMN
                # 注意：如果字段定义中有 COMMENT，保留它
                ddl = f"ALTER TABLE `{table_name}` ADD COLUMN `{col_name}` {col_def};"
                lines.append(ddl)
            lines.append("")

    return '\n'.join(lines)


def main():
    print("正在解析 V001 SQL 文件...")
    schema = parse_sql_schema(SQL_FILE)
    print(f"  发现 {len(schema)} 张表")

    print("正在查询数据库结构...")
    db_cols = get_db_columns()
    print(f"  数据库中共有 {len(db_cols)} 张表")

    print("正在生成差异脚本...")
    ddl = generate_diff_ddl(schema, db_cols)

    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        f.write(ddl)

    print(f"✅ 已生成 {OUTPUT_FILE}")

    # 统计
    missing_tables = [t for t in schema if t not in db_cols]
    missing_cols = 0
    for t, fields in schema.items():
        if t in db_cols:
            for col in fields:
                if col not in db_cols[t]:
                    missing_cols += 1

    print(f"\n统计：")
    print(f"  缺失的表：{len(missing_tables)} 张")
    if missing_tables:
        print(f"    {', '.join(missing_tables)}")
    print(f"  缺失的字段：{missing_cols} 个")


if __name__ == '__main__':
    main()
