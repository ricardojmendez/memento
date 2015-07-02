CREATE TABLE thoughts
(
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  username VARCHAR(30) NOT NULL,
  thought TEXT NOT NULL,
  created TIMESTAMPTZ NOT NULL
);