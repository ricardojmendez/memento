-- :name get-reminder-type :? :1
-- :doc Gets a reminder type by id
SELECT id, schedule from reminder_types where id = :id;

-- :name create-reminder! :<! :1
-- :doc Creates a new reminder for a memory
INSERT INTO reminders (type_id, thought_id, next_date, properties)
VALUES (:type_id, :thought_id, :next_date, :properties)
RETURNING *;

