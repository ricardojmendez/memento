ALTER TABLE thoughts ADD COLUMN root_id UUID REFERENCES thoughts (id);
--;;
ALTER TABLE thoughts ADD COLUMN refine_id UUID REFERENCES thoughts (id);