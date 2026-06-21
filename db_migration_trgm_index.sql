-- PostgreSQL pg_trgm Scalability Fix
-- Run this in your database (e.g. pgAdmin, DBeaver, or psql)
-- It creates a trigram index to vastly improve the speed of queries like:
-- "SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER('%...%')"

-- 1. Enable the pg_trgm extension (Requires superuser or database owner privileges)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. Create the GIN index on the username column for trigram matching
-- Note: Replace 'users' with your actual table name if it's different.
CREATE INDEX IF NOT EXISTS idx_users_username_trgm 
ON users USING gin (LOWER(username) gin_trgm_ops);

-- Once this is executed, PostgreSQL will automatically use this index
-- instead of performing full table scans for frontend tag searches.
