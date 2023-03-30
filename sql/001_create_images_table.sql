CREATE TABLE images (
  id SERIAL PRIMARY KEY,
  image_key UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  url VARCHAR(255) NOT NULL
);
