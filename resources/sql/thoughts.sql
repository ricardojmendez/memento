-- :name create-thought! :<! :1
-- :doc Creates a new thought record
INSERT INTO thoughts (created, username, thought, "root-id", "follow-id")
VALUES (:created, :username, :thought, :root-id, :follow-id)
RETURNING *;


-- :name get-thought-by-id :? :1
-- :doc Returns the thoughts matching an id (which should be only one)
SELECT * FROM thoughts
WHERE id = :id;

-- :name get-thread-by-root-id :? :*
-- :doc Returns all thoughts matching a root id
SELECT * FROM thoughts
WHERE "root-id" = :id
ORDER BY created ASC;


-- :name get-thoughts :? :*
-- :doc Returns all thoughts for a username
SELECT *
FROM thoughts
WHERE username = :username
      AND (:all? OR "archived?" = FALSE)
ORDER BY created DESC
LIMIT :limit
OFFSET :offset;

-- :name get-thought-count :? :1
-- :doc Returns count of all thoughts for a username
SELECT COUNT(*)
FROM thoughts
WHERE username = :username
      AND (:all? OR "archived?" = FALSE);


-- :name update-thought! :<! :1
-- :doc Updates a thought's text and returns it
UPDATE thoughts
SET thought = :thought
WHERE id = :id
RETURNING *;


-- :name archive-thought! :<! :1
-- :doc Updates a thought's archive status and returns the thought
UPDATE thoughts
SET "archived?" = :archived?
WHERE id = :id
RETURNING *;


-- :name make-root! :! :n
-- :doc  Marks a thought as a root by setting its "root-id" to itself.
--  Ensures that the thought isn't refining any other thought before doing so.
UPDATE thoughts
SET "root-id" = id
WHERE id = :id AND "root-id" IS NULL;


-- :name delete-thought! :! :n
-- :doc  Deletes a thought from the database
--  Ensures that the thought isn't refining any other thought before doing so.
DELETE FROM thoughts WHERE id = :id;


-- :name search-thoughts :? :*
-- :doc Returns all thoughts for a username which match a search string
SELECT t.*, ts_rank_cd(tl.lexemes, query) AS rank
FROM to_tsquery('english', :query) AS query, thought_lexemes tl
INNER JOIN thoughts t
  ON t.id = tl.id
WHERE t.username = :username
  AND (:all? OR t."archived?" = FALSE)
  AND tl.lexemes @@ query
ORDER BY rank DESC, created DESC
LIMIT :limit
OFFSET :offset;


-- :name search-thought-count :? :1
-- :doc Returns the count of thoughts for a username which match a search string.
--  Notice we don't care about rank or ordering.
SELECT COUNT(t.*)
FROM to_tsquery('english', :query) AS query, thought_lexemes tl
  INNER JOIN thoughts t
    ON t.id = tl.id
WHERE t.username = :username
      AND tl.lexemes @@ query
      AND (:all? OR t."archived?" = FALSE);