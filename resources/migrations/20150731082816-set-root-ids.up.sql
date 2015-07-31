UPDATE thoughts AS t SET root_id = t.id
FROM  thoughts t2
WHERE t.id = t2.root_id AND t.root_id IS NULL;