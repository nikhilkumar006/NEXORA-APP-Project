# NEXORA database

This folder contains `schema.sql`, copied from the application's `src/main/resources/schema.sql`.

## Import using MySQL Workbench

1. Start MySQL Server and connect in MySQL Workbench.
2. Open `schema.sql` using File → Open SQL Script.
3. Execute the script using the lightning-bolt button.
4. Refresh the Schemas panel and check the `nexora` database and its tables.

The script creates/selects the `nexora` database and then creates the tables defined by the application schema.

## Application connection

The project's `application.properties` defaults to:

- JDBC URL: `jdbc:mysql://localhost:3306/nexora`
- Username: `root`
- Password: empty by default

For your own machine, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as appropriate. Do not commit real passwords or secrets.

The application also has `spring.sql.init.mode=always`, so it initializes the schema from `src/main/resources/schema.sql` at startup. Keep this database copy synchronized with that source file.

This package does not contain a verified sample-data dump. Do not treat an empty schema as populated demo data.
