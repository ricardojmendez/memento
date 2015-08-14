CREATE FUNCTION update_thought_lexeme()
  RETURNS TRIGGER AS '
BEGIN
  UPDATE thought_lexemes
  SET lexemes = to_tsvector(''english'', NEW.thought)
  WHERE id = NEW.id;
  RETURN NULL;
END
' LANGUAGE 'plpgsql';
--;;
CREATE TRIGGER update_lexeme_trigger
AFTER UPDATE ON thoughts
FOR EACH ROW
EXECUTE PROCEDURE update_thought_lexeme();
