-- 14. Kalici oturum (Remember-Me) tablosu
CREATE TABLE persistent_logins (
    username VARCHAR(100) NOT NULL,
    series VARCHAR(64) PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);

CREATE INDEX idx_persistent_logins_username ON persistent_logins(username);
