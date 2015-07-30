DROP TRIGGER lexeme_trigger ON thoughts;
--;;
DROP FUNCTION insert_thought_lexeme();
--;;
CREATE FUNCTION insert_thought_lexeme()
RETURNS TRIGGER AS '
BEGIN
  INSERT INTO thought_lexemes (id, lexemes)
  SELECT NEW.id, to_tsvector(''english'', NEW.thought);
  RETURN NEW;
END
' LANGUAGE 'plpgsql';
--;;
CREATE TRIGGER lexeme_trigger
AFTER INSERT ON thoughts
FOR EACH ROW
EXECUTE PROCEDURE insert_thought_lexeme();