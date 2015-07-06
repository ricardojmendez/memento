CREATE TABLE thought_lexemes
(
  id UUID PRIMARY KEY REFERENCES thoughts(id),
  lexemes TSVECTOR NOT NULL
);
