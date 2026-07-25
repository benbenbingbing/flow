ALTER TABLE sys_user
    ADD COLUMN password_reset_required TINYINT NOT NULL DEFAULT 0;
