CREATE TYPE manga_submission_status AS ENUM ('PENDING', 'REJECTED');

ALTER TABLE mangas
  ADD COLUMN submission_status manga_submission_status,
  ADD COLUMN rejection_reason TEXT,
  ADD COLUMN submitted_at TIMESTAMPTZ,
  ADD COLUMN reviewed_by UUID REFERENCES users(id),
  ADD COLUMN reviewed_at TIMESTAMPTZ;
