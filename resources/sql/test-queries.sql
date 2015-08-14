-- name: clear-thoughts!
-- DELETES ALL THOUGHTS!
DELETE FROM thoughts;

-- name: wipe-database!
-- DELETES ALL USERS, THOUGHTS AND THEIR RECORDS
DELETE FROM thoughts;
DELETE FROM users_roles;
DELETE FROM users;


-- name: update-thought-created!
-- Forces update for a thought's create date, something we will not allow in the real code.
UPDATE thoughts
SET created = :created
WHERE id = :id;
