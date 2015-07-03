-- name: clear-thoughts!
-- DELETES ALL THOUGHTS!
DELETE FROM thoughts;

-- name: wipe-database!
-- DELETES ALL USERS, THOUGHTS AND THEIR RECORDS
DELETE FROM thoughts;
DELETE FROM users_roles;
DELETE FROM users;
