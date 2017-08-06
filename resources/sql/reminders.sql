-- :name get-reminder-type :? :1
-- :doc Gets a reminder type by id
SELECT
  id,
  schedule
FROM reminder_types
WHERE id = :id;

-- :name get-reminder :? :1
-- :doc Returns a reminder by its id, and adds username information
SELECT
  t.username,
  r.*
FROM reminders r
  INNER JOIN thoughts t ON r.thought_id = t.id
WHERE r.id = :id;

-- :name create-reminder! :<! :1
-- :doc Creates a new reminder for a memory
INSERT INTO reminders (type_id, thought_id, next_date, properties)
VALUES (:type_id, :thought_id, :next_date, :properties)
RETURNING *;

