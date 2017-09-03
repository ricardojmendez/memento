-- :name create-user! :! :n
-- :doc Creates a new user record. The password is expected to be bcrypt+sha512,
--  with maximum length of 162 characters, so don't use longer salts than
--  hashers' default.
INSERT INTO users (username, password) VALUES (:username, :password);

-- :name get-user :? :1
-- :doc Looks for a user record based on the username.
SELECT * FROM users WHERE username = :username;