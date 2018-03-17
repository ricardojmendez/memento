CREATE TABLE thought_clusters
(
  id       UUID PRIMARY KEY     DEFAULT uuid_generate_v4(),
  username VARCHAR(30) NOT NULL REFERENCES users (username),
  created  TIMESTAMPTZ NOT NULL DEFAULT now()
);
--;;
CREATE TABLE thought_clusters_thought
(
  "cluster-id" UUID        NOT NULL REFERENCES thought_clusters (id),
  "thought-id" UUID        NOT NULL REFERENCES thoughts (id),
  created      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT cluster_thought PRIMARY KEY ("cluster-id", "thought-id")
);