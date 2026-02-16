ALTER TABLE IF EXISTS users
  DROP COLUMN IF EXISTS children_study_quran,
  DROP COLUMN IF EXISTS children_ages,
  DROP COLUMN IF EXISTS children_count;
