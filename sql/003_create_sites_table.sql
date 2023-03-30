CREATE TABLE sites (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    stage_id UUID REFERENCES stages(id) ON DELETE CASCADE,
    image_id UUID REFERENCES images(image_key) ON DELETE CASCADE
);