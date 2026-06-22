-- Fix NULL settings for older communities
UPDATE communities 
SET chat_retention_days = 0 
WHERE chat_retention_days IS NULL;

UPDATE communities 
SET is_group_chat_enabled = true 
WHERE is_group_chat_enabled IS NULL;
