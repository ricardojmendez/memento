-- :name create-resolution! :<! :1
-- :doc Creates a new resolution
INSERT INTO resolutions (username, subject, description, alternatives, outcomes, tags)
VALUES (:username, :subject, :description, :alternatives, :outcomes, :tags)
RETURNING *;

-- :name get-resolution :? :1
-- :doc Returns the resolution matching an id
SELECT * FROM resolutions
WHERE id = :id;



-- :name get-resolutions :? :*
-- :doc Returns all resolutions for a username
SELECT *
FROM resolutions
WHERE username = :username
      AND (:all? OR "archived?" = FALSE)
ORDER BY created DESC;