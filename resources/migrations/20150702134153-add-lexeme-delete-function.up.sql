CREATE FUNCTION delete_thought_lexeme()
  RETURNS TRIGGER AS '
BEGIN
  DELETE FROM thought_lexemes
  WHERE id = OLD.id;
  RETURN OLD;
END
' LANGUAGE 'plpgsql';
--;;
CREATE TRIGGER lexeme_delete_trigger
BEFORE DELETE ON thoughts
FOR EACH ROW
EXECUTE PROCEDURE delete_thought_lexeme();
