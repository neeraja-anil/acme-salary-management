-- Usernames are case-sensitive (sign-in must match the exact case), but two usernames that differ only in
-- case would look like the same person, e.g. "admin" and "Admin" in the change log. The service refuses
-- them; this index makes the database refuse them too.
CREATE UNIQUE INDEX ux_app_user_username_nocase ON app_user (username COLLATE NOCASE);
