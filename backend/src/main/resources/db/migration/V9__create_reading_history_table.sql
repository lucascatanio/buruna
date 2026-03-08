CREATE TABLE reading_history
(
    id        UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    user_id   UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    volume_id UUID                     NOT NULL REFERENCES volumes (id) ON DELETE CASCADE,
    read_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_reading_history_user_id ON reading_history (user_id);