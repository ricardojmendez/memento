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

-- :name get-pending-reminders :? :*
-- :doc Returns all reminders for a user where the next_date is before a received data
SELECT
  t.username,
  t.thought,
  r.*
FROM reminders r
  INNER JOIN thoughts t ON r.thought_id = t.id
WHERE r.next_date <= :min_date
      AND t.username = :username
ORDER BY r.next_date;

-- :name update-reminder-date! :! :n
-- :doc Updates a reminder's next remind date. Returns the number of records updated.
UPDATE reminders
SET next_date = :next_date, properties = :properties
WHERE id = :id;

-- :name get-active-reminders-for-thought :? :*
-- :doc Returns all reminders for a thought that are considered active (have a next_date). Will filter by username.
SELECT r.*
FROM reminders r
  INNER JOIN thoughts t ON r.thought_id = :id
WHERE r.next_date IS NOT NULL
      AND t.username = :username
ORDER BY r.next_date;