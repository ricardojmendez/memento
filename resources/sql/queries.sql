-- name: create-thought!
-- Creates a new thought record
INSERT INTO thoughts (created, username, thought) VALUES (:created, :username, :thought)


-- name: get-thoughts
-- Returns all thoughts for a username
SELECT * FROM thoughts
WHERE username = :username
ORDER BY created DESC
LIMIT :limit
OFFSET :offset


-- name: search-thoughts
-- Returns all thoughts for a username which match a search string
SELECT t.*, ts_rank_cd(tl.lexemes, query) AS rank
FROM to_tsquery(:query) AS query, thought_lexemes tl
INNER JOIN thoughts t
  ON t.id = tl.id
WHERE t.username = :username
  AND tl.lexemes @@ query
ORDER BY rank DESC
LIMIT :limit
OFFSET :offset
