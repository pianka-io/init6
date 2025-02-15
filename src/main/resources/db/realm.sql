CREATE TABLE IF NOT EXISTS realm_cookies (
    id INT NOT NULL AUTO_INCREMENT,
    user_id INT(11),
    PRIMARY KEY(id)
);

CREATE TABLE IF NOT EXISTS realm_characters (
    id INT NOT NULL AUTO_INCREMENT,
    user_id INT(11),
    name VARCHAR(16),
    class INT,
    flags INT,
    ladder INT,
    statstring VARBINARY(33),
    PRIMARY KEY(id)
);