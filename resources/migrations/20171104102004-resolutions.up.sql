CREATE TABLE resolutions
(
  id           UUID PRIMARY KEY      DEFAULT uuid_generate_v4(),
  username     VARCHAR(30)  NOT NULL REFERENCES users (username),
  created      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  subject      VARCHAR(100) NOT NULL,
  "archived?"  BOOLEAN      NOT NULL DEFAULT FALSE,
  description  TEXT         NOT NULL,
  alternatives TEXT,
  outcomes     TEXT,
  tags         TEXT
);
--;;
CREATE INDEX resolutions_created_idx
  ON resolutions (created);
--;;
CREATE INDEX resolutions_archived_idx
  ON resolutions ("archived?");
--;;
CREATE INDEX resolutions_username
  ON resolutions (username);