-- :name create-cluster! :<! :1
-- :doc Creates a new thought cluster with a random ID
INSERT INTO thought_clusters (id, username)
VALUES (uuid_generate_v4(), :username)
RETURNING *;

-- :name add-thoughts-to-cluster! :! :n
-- :doc Adds a list of thought-cluster pairs
INSERT INTO thought_clusters_thought ("cluster-id", "thought-id")
VALUES :t*:pairs;

-- :snip join-cluster-thoughts
-- :doc Extends a though query to join by clusters
INNER JOIN thought_clusters_thought tct
ON t.id = tct."thought-id"
INNER JOIN thought_clusters tc
ON tct."cluster-id" = tc.id
AND tc.id = :id
AND tc.username = :username


-- :name get-clusters :? :*
-- :doc Returns the list of clusters for a username
SELECT * from thought_clusters
WHERE username = :username
ORDER BY created DESC;

-- :name remove-thought-from-cluster! :! :n
-- :doc Removes a thought from a cluster only if the cluster belongs to a user
DELETE FROM thought_clusters_thought tct
USING thought_clusters tc
WHERE tct."thought-id" = :thought-id
  AND tc.id = :cluster-id
  AND tc.id = tct."cluster-id"
  AND tc.username = :username;

-- :name delete-cluster-if-empty! :! :n
-- :doc Removes a cluster if it's empty. Doesn't validate against the user because we don't want empty clusters anyway.
DELETE FROM thought_clusters tc
WHERE tc.id = :id
AND (SELECT count(*) FROM thought_clusters_thought tct
     WHERE tct."cluster-id" = :id) = 0;