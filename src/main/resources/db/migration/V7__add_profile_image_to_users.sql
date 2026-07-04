-- V7__add_profile_image_to_users.sql

ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url TEXT;
