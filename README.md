# memento

Work in progress, pre-alpha experimental project.

[![build status](https://gitlab.com/ricardojmendez/memento/badges/develop/build.svg)](https://gitlab.com/ricardojmendez/memento/commits/develop)

## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

You'll need also need a PostgreSQL 9.4.4 database running. The default configuration assumes it's on localhost.


## Installation

### Creating the test and dev databases

Let's use the provided script to set up the database:

```shell
psql -d postgres < db-setup.sql
```

This will create test and development databases, add the necessary extensions, and create a test user.

Then run the migrations on both dev and test with:

```shell
lein run migrate
lein with-profile test run migrate
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
