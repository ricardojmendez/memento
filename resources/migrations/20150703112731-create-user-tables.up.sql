CREATE TABLE users
(
  username VARCHAR(30) PRIMARY KEY,
  password VARCHAR(162) NOT NULL -- bcrypt+sha512 and its salt
);
--;;
CREATE TABLE roles
(
  id VARCHAR(10) PRIMARY KEY
);
--;;
CREATE TABLE users_roles
(
  role_id  VARCHAR(10) NOT NULL REFERENCES roles (id),
  username VARCHAR(30) NOT NULL REFERENCES users (username),
  CONSTRAINT role_user PRIMARY KEY (role_id, username)
);
--;;
INSERT INTO users
(username, password)
  SELECT DISTINCT
    username,
    'change-to-hash'
  FROM thoughts;
--;;
ALTER TABLE thoughts
ADD CONSTRAINT username_fk FOREIGN KEY (username) REFERENCES users (username);
