-- :name clear-thoughts! :! :n
-- :doc DELETES ALL THOUGHTS!
DELETE FROM thoughts;

-- :name wipe-database! :! :n
-- :doc DELETES ALL USERS, THOUGHTS AND THEIR RECORDS
DELETE FROM reminders;
DELETE FROM thoughts;
DELETE FROM users_roles;
DELETE FROM users;


-- :name update-thought-created! :! :n
-- :doc Forces update for a thought's create date, something we will not allow in the real code.
UPDATE thoughts
SET created = :created
WHERE id = :id;

-- :name get-all-reminders :? :*
-- :doc Returns all reminders in the database
SELECT * FROM reminders ORDER BY created;