-- Removes everything generate-synthetic-data.sql created (the foreign keys cascade to the child tables).
DELETE FROM songs WHERE title LIKE 'SYNTHETIC #%';
