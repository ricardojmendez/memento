-- :name create-cluster! :<! :1
-- :doc Creates a new thought cluster with a random ID
INSERT INTO thought_clusters (id, username)
VALUES (uuid_generate_v4(), :username)
RETURNING *;

-- :name add-thoughts-to-cluster! :! :n
-- :doc Adds a list of thought-cluster pairs
INSERT INTO thought_clusters_thought ("cluster-id", "thought-id")
VALUES :t*:pairs;

-- :name get-cluster-thoughts :? :*
-- :doc Returns the thoughts for a cluster id, validating the username
SELECT "thought-id" from thought_clusters_thought tct
INNER JOIN thought_clusters tc ON tct."cluster-id" = tc.id
WHERE tct."cluster-id" = :id
  AND tc.username = :username;

-- :name get-clusters :? :*
-- :doc Returns the list of clusters for a username
SELECT * from thought_clusters
WHERE username = :username
ORDER BY created DESC;