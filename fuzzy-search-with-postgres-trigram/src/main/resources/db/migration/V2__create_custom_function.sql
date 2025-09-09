CREATE OR REPLACE FUNCTION trgm_word_similarity(text, text)
RETURNS boolean AS $$
BEGIN
RETURN $1 <% $2;
END;
$$ LANGUAGE plpgsql IMMUTABLE;