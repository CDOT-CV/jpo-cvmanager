#!/usr/bin/env python3
"""
Compare two SQL schema export files containing CREATE TABLE statements.

Input 1: intended state
Input 2: actual state

Output order:
1. Tables missing in actual
2. Tables unexpected in actual
3. For shared tables, property/constraint mismatches with both values
"""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Tuple


@dataclass
class TableDef:
    name: str
    properties: Dict[str, str] = field(default_factory=dict)
    constraints: Dict[str, str] = field(default_factory=dict)


def normalize_ws(value: str) -> str:
    return " ".join(value.strip().split())


def normalize_identifier(value: str) -> str:
    # Preserve quoted identifiers as-is, normalize unquoted to lower case.
    value = value.strip()
    if value.startswith('"') and value.endswith('"'):
        return value
    return value.lower()


def extract_create_table_blocks(sql_text: str) -> List[Tuple[str, str]]:
    """
    Return list of (table_name, table_body) for CREATE TABLE ... (...);
    Uses parser-like scanning to handle multiline content reliably.
    """
    blocks: List[Tuple[str, str]] = []
    pattern = re.compile(r"CREATE\s+TABLE\s+([\w\.\"`]+)\s*\(", re.IGNORECASE)

    for match in pattern.finditer(sql_text):
        table_name = match.group(1)
        i = match.end()  # position right after opening '('
        depth = 1
        in_single = False
        in_double = False

        while i < len(sql_text) and depth > 0:
            ch = sql_text[i]
            prev = sql_text[i - 1] if i > 0 else ""

            if ch == "'" and not in_double and prev != "\\":
                in_single = not in_single
            elif ch == '"' and not in_single and prev != "\\":
                in_double = not in_double
            elif not in_single and not in_double:
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1

            i += 1

        if depth != 0:
            continue

        # body excludes final matching ')'
        body = sql_text[match.end() : i - 1]

        # Require trailing semicolon to avoid false positives
        j = i
        while j < len(sql_text) and sql_text[j].isspace():
            j += 1
        if j < len(sql_text) and sql_text[j] == ";":
            blocks.append((table_name, body))

    return blocks


def split_top_level_items(body: str) -> List[str]:
    """Split table body by top-level commas."""
    items: List[str] = []
    buf: List[str] = []
    depth = 0
    in_single = False
    in_double = False

    i = 0
    while i < len(body):
        ch = body[i]
        prev = body[i - 1] if i > 0 else ""

        if ch == "'" and not in_double and prev != "\\":
            in_single = not in_single
        elif ch == '"' and not in_single and prev != "\\":
            in_double = not in_double
        elif not in_single and not in_double:
            if ch == "(":
                depth += 1
            elif ch == ")" and depth > 0:
                depth -= 1
            elif ch == "," and depth == 0:
                piece = "".join(buf).strip()
                if piece:
                    items.append(piece)
                buf = []
                i += 1
                continue

        buf.append(ch)
        i += 1

    tail = "".join(buf).strip()
    if tail:
        items.append(tail)

    return items


def parse_property(item: str) -> Tuple[str, str] | None:
    # Handles quoted column names like "timestamp"
    m = re.match(r'^("(?:[^"]|"")*"|\S+)\s+(.+)$', item, flags=re.DOTALL)
    if not m:
        return None
    prop_name = normalize_identifier(m.group(1))
    prop_value = normalize_ws(item)
    return prop_name, prop_value


def parse_constraint(item: str) -> Tuple[str, str] | None:
    m = re.match(r'^CONSTRAINT\s+("(?:[^"]|"")*"|\S+)\s+(.+)$', item, flags=re.IGNORECASE | re.DOTALL)
    if not m:
        return None
    constraint_name = normalize_identifier(m.group(1))
    constraint_value = normalize_ws(item)
    return constraint_name, constraint_value


def parse_schema(path: Path) -> Dict[str, TableDef]:
    text = path.read_text(encoding="utf-8")
    tables: Dict[str, TableDef] = {}

    for raw_table_name, body in extract_create_table_blocks(text):
        table_name = normalize_identifier(raw_table_name)
        table = TableDef(name=table_name)

        for item in split_top_level_items(body):
            c = parse_constraint(item)
            if c:
                cname, cval = c
                table.constraints[cname] = cval
                continue

            p = parse_property(item)
            if p:
                pname, pval = p
                table.properties[pname] = pval

        tables[table_name] = table

    return tables


def compare_tables(intended: Dict[str, TableDef], actual: Dict[str, TableDef]) -> str:
    lines: List[str] = []

    intended_tables = set(intended.keys())
    actual_tables = set(actual.keys())

    missing_tables = sorted(intended_tables - actual_tables)
    extra_tables = sorted(actual_tables - intended_tables)
    shared_tables = sorted(intended_tables & actual_tables)

    lines.append("Schema Comparison Report")
    lines.append("========================")
    lines.append("")

    lines.append("Tables missing in actual (should exist)")
    lines.append("---------------------------------------")
    if missing_tables:
        for t in missing_tables:
            lines.append(f"- {t}")
    else:
        lines.append("- None")
    lines.append("")

    lines.append("Tables unexpected in actual (should not exist)")
    lines.append("-----------------------------------------------")
    if extra_tables:
        for t in extra_tables:
            lines.append(f"- {t}")
    else:
        lines.append("- None")
    lines.append("")

    lines.append("Mismatches in shared tables")
    lines.append("---------------------------")

    shared_mismatch_found = False

    for table_name in shared_tables:
        t_int = intended[table_name]
        t_act = actual[table_name]

        table_lines: List[str] = []

        prop_keys = sorted(set(t_int.properties) | set(t_act.properties))
        for key in prop_keys:
            i_val = t_int.properties.get(key)
            a_val = t_act.properties.get(key)
            if i_val != a_val:
                table_lines.append(f"  Property: {key}")
                table_lines.append(f"    intended: {i_val if i_val is not None else '<MISSING>'}")
                table_lines.append(f"    actual:   {a_val if a_val is not None else '<MISSING>'}")

        con_keys = sorted(set(t_int.constraints) | set(t_act.constraints))
        for key in con_keys:
            i_val = t_int.constraints.get(key)
            a_val = t_act.constraints.get(key)
            if i_val != a_val:
                table_lines.append(f"  Constraint: {key}")
                table_lines.append(f"    intended: {i_val if i_val is not None else '<MISSING>'}")
                table_lines.append(f"    actual:   {a_val if a_val is not None else '<MISSING>'}")

        if table_lines:
            shared_mismatch_found = True
            lines.append(f"Table: {table_name}")
            lines.extend(table_lines)
            lines.append("")

    if not shared_mismatch_found:
        lines.append("No property/constraint mismatches found in shared tables.")
        lines.append("")

    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Compare intended and actual schema export SQL files."
    )
    parser.add_argument("intended", help="Path to intended-state SQL export file")
    parser.add_argument("actual", help="Path to actual-state SQL export file")
    parser.add_argument(
        "-o",
        "--output",
        help="Optional report output file path. If omitted, prints to stdout.",
    )

    args = parser.parse_args()

    intended_path = Path(args.intended)
    actual_path = Path(args.actual)

    intended = parse_schema(intended_path)
    actual = parse_schema(actual_path)

    report = compare_tables(intended, actual)

    if args.output:
        Path(args.output).write_text(report, encoding="utf-8")
        print(f"Wrote report to {args.output}")
    else:
        print(report)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
