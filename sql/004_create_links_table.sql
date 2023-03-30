CREATE TABLE links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    origin UUID REFERENCES sites(id) ON DELETE CASCADE,
    destination UUID REFERENCES sites(id) ON DELETE CASCADE
    location_x INTEGER NOT NULL,
    location_y INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL
);