-- Phòng nhóm giáo viên đang ở (NULL = phòng chính) — để FE khôi phục vị trí GV sau khi reload
ALTER TABLE breakout_sessions
    ADD COLUMN teacher_room_id UUID REFERENCES breakout_rooms (id) ON DELETE SET NULL;
