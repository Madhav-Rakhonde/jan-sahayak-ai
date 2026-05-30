-- Master Query to delete users and all their associated records safely
-- Run this in your Supabase SQL Editor

DO $$ 
DECLARE
    -- The list of user IDs to delete
    target_users BIGINT[] := ARRAY[42, 17, 18, 6, 21, 45];
BEGIN
    
    -- ==========================================
    -- 1. DELETE INTERACTIONS ON USERS' ENTITIES
    -- ==========================================
    -- Before we can delete the posts/communities created by these users, 
    -- we must delete all child records attached to them (even if created by others).

    -- 1A. Cleanup dependencies on the users' POSTS
    DELETE FROM user_tags WHERE post_id IN (SELECT id FROM posts WHERE user_id = ANY(target_users));
    DELETE FROM post_likes WHERE post_id IN (SELECT id FROM posts WHERE user_id = ANY(target_users));
    DELETE FROM post_views WHERE post_id IN (SELECT id FROM posts WHERE user_id = ANY(target_users));
    DELETE FROM post_shares WHERE post_id IN (SELECT id FROM posts WHERE user_id = ANY(target_users));
    DELETE FROM saved_posts WHERE post_id IN (SELECT id FROM posts WHERE user_id = ANY(target_users));
    DELETE FROM comments WHERE post_id IN (SELECT id FROM posts WHERE user_id = ANY(target_users));
    DELETE FROM content_reports WHERE target_type = 'POST' AND target_id IN (SELECT id FROM posts WHERE user_id = ANY(target_users));

    -- 1B. Cleanup dependencies on the users' SOCIAL POSTS
    DELETE FROM user_tags WHERE social_post_id IN (SELECT id FROM social_posts WHERE user_id = ANY(target_users));
    DELETE FROM post_likes WHERE social_post_id IN (SELECT id FROM social_posts WHERE user_id = ANY(target_users));
    DELETE FROM post_views WHERE social_post_id IN (SELECT id FROM social_posts WHERE user_id = ANY(target_users));
    DELETE FROM post_shares WHERE social_post_id IN (SELECT id FROM social_posts WHERE user_id = ANY(target_users));
    DELETE FROM saved_posts WHERE social_post_id IN (SELECT id FROM social_posts WHERE user_id = ANY(target_users));
    DELETE FROM comments WHERE social_post_id IN (SELECT id FROM social_posts WHERE user_id = ANY(target_users));
    DELETE FROM content_reports WHERE target_type = 'SOCIAL_POST' AND target_id IN (SELECT id FROM social_posts WHERE user_id = ANY(target_users));

    -- 1C. Cleanup dependencies on the users' POLLS
    DELETE FROM poll_votes WHERE poll_id IN (
        SELECT id FROM polls WHERE created_by_user_id = ANY(target_users) 
        OR social_post_id IN (SELECT id FROM social_posts WHERE user_id = ANY(target_users))
    );
    DELETE FROM poll_options WHERE poll_id IN (
        SELECT id FROM polls WHERE created_by_user_id = ANY(target_users) 
        OR social_post_id IN (SELECT id FROM social_posts WHERE user_id = ANY(target_users))
    );

    -- 1D. Cleanup dependencies on the users' COMMUNITIES
    DELETE FROM community_invites WHERE community_id IN (SELECT id FROM communities WHERE owner_id = ANY(target_users));
    DELETE FROM community_join_requests WHERE community_id IN (SELECT id FROM communities WHERE owner_id = ANY(target_users));
    DELETE FROM community_members WHERE community_id IN (SELECT id FROM communities WHERE owner_id = ANY(target_users));

    -- ==========================================
    -- 2. DELETE USERS' OWN INTERACTIONS
    -- ==========================================
    -- Now we delete the users' activities across the rest of the platform.

    DELETE FROM user_tags WHERE tagged_user_id = ANY(target_users) OR tagged_by_user_id = ANY(target_users);
    DELETE FROM post_likes WHERE user_id = ANY(target_users);
    DELETE FROM post_views WHERE user_id = ANY(target_users);
    DELETE FROM post_shares WHERE user_id = ANY(target_users);
    DELETE FROM saved_posts WHERE user_id = ANY(target_users);
    DELETE FROM comments WHERE user_id = ANY(target_users);
    DELETE FROM poll_votes WHERE user_id = ANY(target_users);
    DELETE FROM content_reports WHERE reporter_id = ANY(target_users) OR resolved_by_id = ANY(target_users);
    DELETE FROM notifications WHERE user_id = ANY(target_users) OR triggered_by_user_id = ANY(target_users);
    DELETE FROM community_invites WHERE inviter_id = ANY(target_users) OR invitee_id = ANY(target_users);
    DELETE FROM community_join_requests WHERE user_id = ANY(target_users);
    DELETE FROM community_members WHERE user_id = ANY(target_users);
    DELETE FROM user_interest_profiles WHERE user_id = ANY(target_users);

    -- ==========================================
    -- 3. DELETE THE MAIN ENTITIES OWNED BY USERS
    -- ==========================================
    DELETE FROM posts WHERE user_id = ANY(target_users);
    
    -- Delete polls BEFORE social_posts to satisfy fk_poll_social_post constraint
    DELETE FROM polls WHERE created_by_user_id = ANY(target_users) 
        OR social_post_id IN (SELECT id FROM social_posts WHERE user_id = ANY(target_users));
        
    DELETE FROM social_posts WHERE user_id = ANY(target_users);
    DELETE FROM communities WHERE owner_id = ANY(target_users);

    -- ==========================================
    -- 4. DELETE THE USERS THEMSELVES
    -- ==========================================
    -- Unset self-referencing foreign keys (if a user created another user) to prevent constraint errors
    UPDATE users SET created_by = NULL WHERE created_by = ANY(target_users);
    
    -- Finally, delete the users
    DELETE FROM users WHERE id = ANY(target_users);

END $$;
