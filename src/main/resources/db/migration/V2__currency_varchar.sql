-- CHAR(3) maps to bpchar in Postgres, which fails Hibernate schema validation
-- against the String/varchar mapping on the entity. Align the column type.
ALTER TABLE payments ALTER COLUMN currency TYPE VARCHAR(3);
