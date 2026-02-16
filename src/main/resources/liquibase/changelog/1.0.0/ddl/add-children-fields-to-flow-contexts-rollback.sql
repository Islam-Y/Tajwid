ALTER TABLE IF EXISTS flow_contexts
  DROP COLUMN IF EXISTS children_age_index,
  DROP COLUMN IF EXISTS children_study_quran,
  DROP COLUMN IF EXISTS children_ages,
  DROP COLUMN IF EXISTS children_count;
