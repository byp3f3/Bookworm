-- Create shelves table
CREATE TABLE shelves (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT,
  user_id TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create book_shelf relationship table
CREATE TABLE book_shelf (
  book_id TEXT NOT NULL,
  shelf_id TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (book_id, shelf_id)
);

-- Create indexes for faster querying
CREATE INDEX idx_shelves_user_id ON shelves(user_id);
CREATE INDEX idx_book_shelf_shelf_id ON book_shelf(shelf_id);
CREATE INDEX idx_book_shelf_book_id ON book_shelf(book_id);

-- Set up Row Level Security (RLS) policies
ALTER TABLE shelves ENABLE ROW LEVEL SECURITY;
ALTER TABLE book_shelf ENABLE ROW LEVEL SECURITY;

-- Create policy for shelves that allows users to see only their shelves
CREATE POLICY shelves_policy ON shelves
  USING (auth.uid()::text = user_id);

-- Create policy for book_shelf that allows users to see book-shelf relationships
-- for shelves they own
CREATE POLICY book_shelf_policy ON book_shelf
  USING (shelf_id IN (SELECT id FROM shelves WHERE user_id = auth.uid()::text));

-- Create functions for updating timestamps
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updating timestamps
CREATE TRIGGER update_shelves_updated_at
  BEFORE UPDATE ON shelves
  FOR EACH ROW
  EXECUTE PROCEDURE update_updated_at(); 