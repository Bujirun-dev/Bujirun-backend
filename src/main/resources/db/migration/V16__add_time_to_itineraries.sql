-- V16__add_time_to_itineraries.sql
ALTER TABLE itineraries ADD COLUMN IF NOT EXISTS start_time TIME;
ALTER TABLE itineraries ADD COLUMN IF NOT EXISTS end_time TIME;