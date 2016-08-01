# memento

Work in progress, pre-alpha experimental project.

## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

You'll need also need a PostgreSQL 9.4.4 database running. The default configuration assumes it's on localhost.


## Installation

### Creating the test and dev databases

First, connect to PostgreSQL:

```shell
psql -d postgres
```

Once there, first create the user and then the two databases we'll use:

```sql
CREATE USER memento WITH PASSWORD 'testdb';
CREATE DATABASE memento_dev WITH OWNER memento;
CREATE DATABASE memento_test WITH OWNER memento;
```

Yes, it's a horribly weak password - we'll use it only for testing and development.  The project configuration does not (and should not) embed the live password.

We then create the extensions for UUID (still within psql):

```sql
\c memento_dev
CREATE EXTENSION "uuid-ossp";
\c memento_test
CREATE EXTENSION "uuid-ossp";
```

While we could create the extensions on a migration, that would require the memento user to be a superuser. Let's not go that far.

Then run the migrations on both dev and test with:

```shell
lein migratus migrate
lein with-profile test migratus migrate
```


## Running

To start a web server for the application, run:

    lein run

## Testing

Running `lein test` will run the tests against an index `memento-test` on the local host.  

If you're using Cursive Clojure, bear in mind it does not yet support a way to launch a REPL with specific environment profile. Since the application reads its database connection parameters from the environment configuration, if you start a REPL from Cursive and run the tests against it, you'll be running them against the development database and not the test one.

Make sure you either create a REPL profile specifically for the test settings, or just run the tests via lein.

## License

Copyright © 2015 Numergent Limited.
