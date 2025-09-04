INSERT INTO role (id, role_name)
VALUES
    (1, 'ADMIN'),
    (2, 'USER'),
    (3, 'STAFF');

-- Insert 2 robots with auto-generated UUIDs
INSERT INTO robot (id, code, battery_status, location_realtime)
VALUES
    (UUID_TO_BIN(UUID()), 'ROBOT-001', '85', '37.422740,-122.084058'),
    (UUID_TO_BIN(UUID()), 'ROBOT-002', '72', '37.425670,-122.089834');

SET @robot1 := (SELECT id FROM robot WHERE code = 'ROBOT001' LIMIT 1);
SET @robot2 := (SELECT id FROM robot WHERE code = 'ROBOT002' LIMIT 1);

INSERT INTO robot_container (robot_id, container_code, status)
VALUES
    -- Containers for Robot 1
    (@robot1, 'R-001_C-1', 'free'),
    (@robot1, 'R-001_C-2', 'non-free'),

    -- Containers for Robot 2
    (@robot2, 'R-002_C-1', 'free'),
    (@robot2, 'R-002_C-2', 'non-free');

