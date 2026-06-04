-- Avatar URLs were stored as absolute MinIO URLs (e.g. http://localhost:9000/classpulse/avatars/x.jpg).
-- An absolute localhost URL cannot be loaded from another device (a phone on the LAN reaches its own
-- localhost). Convert existing rows to a relative /storage/... path served via the frontend proxy.
-- http://localhost:9000/classpulse/avatars/x.jpg  ->  /storage/classpulse/avatars/x.jpg
UPDATE users
SET avatar_url = regexp_replace(avatar_url, '^https?://[^/]+', '/storage')
WHERE avatar_url ~ '^https?://';
