ALTER TABLE flow_contexts
  ADD COLUMN children_count INT,
  ADD COLUMN children_ages VARCHAR(255),
  ADD COLUMN children_study_quran BOOLEAN,
  ADD COLUMN children_age_index INT;
