CREATE USER memento WITH PASSWORD 'testdb';
CREATE DATABASE memento_dev WITH OWNER memento;
CREATE DATABASE memento_test WITH OWNER memento;
\c memento_dev
CREATE EXTENSION "uuid-ossp";
\c memento_test
CREATE EXTENSION "uuid-ossp";

