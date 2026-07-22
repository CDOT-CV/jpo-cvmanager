# PostgreSQL database diffs

## Methodology
Exported db configuration with the following sql query:
```sql
SELECT 
    'CREATE TABLE ' || quote_ident(n.nspname) || '.' || quote_ident(c.relname) || ' (' || E'\n' ||
    -- Columns (Types, Defaults, NOT NULL)
    string_agg(
        '  ' || quote_ident(a.attname) || ' ' || 
        pg_catalog.format_type(a.atttypid, a.atttypmod) ||
        CASE 
            WHEN d.adbin IS NOT NULL THEN ' DEFAULT ' || pg_get_expr(d.adbin, d.adrelid) 
            ELSE '' 
        END ||
        CASE 
            WHEN a.attnotnull THEN ' NOT NULL' 
            ELSE '' 
        END,
        ',' || E'\n'
    ) || 
    -- Table Constraints (Primary Keys, Foreign Keys, Unique, Check)
    COALESCE((
        SELECT E',\n' || string_agg('  CONSTRAINT ' || quote_ident(con.conname) || ' ' || pg_get_constraintdef(con.oid), E',\n')
        FROM pg_constraint con
        WHERE con.conrelid = c.oid 
          AND con.contype IN ('p', 'f', 'u', 'c')
    ), '') ||
    E'\n);' AS create_table_ddl
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_attribute a ON a.attrelid = c.oid
LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
WHERE c.relkind = 'r' 
  AND n.nspname = 'public' -- Change if using a non-public schema
  AND a.attnum > 0 
  AND NOT a.attisdropped
GROUP BY c.oid, c.relname, n.nspname
ORDER BY c.relname;
```