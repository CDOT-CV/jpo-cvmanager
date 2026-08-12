# PostgreSQL database diffs

Before integrating flyway into a deployment, it is incredibly important to ensure that the database schema matches the expected baseline. Once flyway has been run, it is difficult to roll back changes. This document describes a process for comparing the current database schema to a baseline schema.

## Verification before Flyway integration

1. Export the schema from the database to a SQL file.
This is the most manual step of the process. This step requires a sql query to be run against the desired database. pgAdmin4 is recommended, using the Query Tool to run the query and export the results to a SQL file. The query is available in [resources/db/schema_migration_checks/export_schema_ddl.sql](c:/Users/rando/Documents/GitHub/jpo-cvmanager/resources/db/schema_migration_checks/export_schema_ddl.sql).

After executing the query, export the results to a SQL file. pgAdmin4 exports to a CSV file. TO convert that CSV file to a .sql, use the following script:
```sh
bash csv_to_sql.sh ./{exported_schema}.csv
```

2. Compare the exported SQL file to a baseline SQL file (V1__baseline_state.sql).
This step involves running a Python script, which will compare the two SQL files and generate a report of the differences. The script is available in [resources/db/schema_migration_checks/compare_schema_exports.py](c:/Users/rando/Documents/GitHub/jpo-cvmanager/resources/db/schema_migration_checks/compare_schema_exports.py).
```sh
python compare_schema_exports.py ./schema_exports/V1__baseline_state.sql {exported_schema}.sql -o schema_compare_report_example.txt
```

3. Review the report and determine if any changes are needed to the migration scripts.
4. Apply any necessary changes manually to the database. 