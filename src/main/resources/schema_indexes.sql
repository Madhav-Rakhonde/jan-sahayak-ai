-- Supabase Composite Indexes for Jan-Sahayak-AI Feed Optimization
-- Execute these SQL queries in your Supabase SQL Editor to speed up feed loading.

-- 1. Local Feed Candidate Index
CREATE INDEX IF NOT EXISTS idx_social_post_local_feed 
ON social_posts (pincode, status, is_flagged, quality_score, created_at DESC);

-- 2. District Viral Feed Candidate Index
CREATE INDEX IF NOT EXISTS idx_social_post_district_viral 
ON social_posts (district_prefix, viral_tier, status, is_flagged, virality_score DESC);

-- 3. State Viral Feed Candidate Index
CREATE INDEX IF NOT EXISTS idx_social_post_state_viral 
ON social_posts (state_prefix, viral_tier, status, is_flagged, virality_score DESC);

-- 4. National Viral Feed Candidate Index
CREATE INDEX IF NOT EXISTS idx_social_post_national_viral 
ON social_posts (viral_tier, status, is_flagged, virality_score DESC);
