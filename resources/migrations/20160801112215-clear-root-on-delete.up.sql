CREATE FUNCTION clear_root_on_delete()
  RETURNS TRIGGER AS '
BEGIN
  IF (SELECT COUNT(*) FROM thoughts
      WHERE root_id = OLD.root_id) = 1::BIGINT THEN
    UPDATE thoughts
    SET root_id = NULL
    WHERE id = OLD.root_id;
  END IF;
  RETURN OLD;
END
' LANGUAGE 'plpgsql';
--;;
--;;
CREATE TRIGGER thought_root_clear_trigger
AFTER DELETE ON thoughts
FOR EACH ROW
EXECUTE PROCEDURE clear_root_on_delete();
