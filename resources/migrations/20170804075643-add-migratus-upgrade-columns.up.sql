DO $$
BEGIN
  BEGIN
    ALTER TABLE schema_migrations ADD COLUMN description varchar(1024);
    EXCEPTION
    WHEN duplicate_column THEN RAISE NOTICE 'column description already exists in schema_migrations.';
  END;
END;
$$
--;;
DO $$
BEGIN
  BEGIN
    ALTER TABLE schema_migrations ADD COLUMN applied timestamp;
    EXCEPTION
    WHEN duplicate_column THEN RAISE NOTICE 'column applied already exists in schema_migrations.';
  END;
END;
$$
--;;
