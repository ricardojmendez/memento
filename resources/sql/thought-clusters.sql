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