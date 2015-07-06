-- name: create-thought!
-- Creates a new thought record
INSERT INTO thoughts (created, username, thought) VALUES (:created, :username, :thought);


-- name: get-thoughts
-- Returns all thoughts for a username
SELECT * FROM thoughts
WHERE username = :username
ORDER BY created DESC
LIMIT :limit
OFFSET :offset;


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
OFFSET :offset;


-- name: create-user!
-- Creates a new user record. The password is expected to be bcrypt+sha512,
-- with maximum length of 162 characters, so don't use longer salts than
-- hashers' default.
INSERT INTO users (username, password) VALUES (:username, :password);

-- name: get-user
-- Looks for a user record based on the username.
SELECT * FROM users WHERE username = :username;