-- CHAR(1) → VARCHAR(1): Hibernate 6 schema validation 호환
ALTER TABLE itineraries ALTER COLUMN plan_type TYPE VARCHAR(1);
