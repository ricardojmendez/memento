# memento

Memento started as an experiment on introspection - private note-taking for thoughts you may want to revisit in the future.

It's a work in progress, alpha experimental project.

[![build status](https://gitlab.com/Numergent/memento/badges/master/build.svg)](https://gitlab.com/Numergent/memento/commits/master)

## Live version 

[You can find a live version here](https://mementoapp.herokuapp.com/). It's on Heroku running on free dynos, so you may need to wait while it wakes up.

The user name does not need to be an e-mail address, although that will facilitate password reset in the future. You can use whatever login you fancy, though. 

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

### Deploying live

Memento uses token authentication. The tokens are signed using a private key, which is expected to be configured in the environment. 

The repository includes a private/public key pair for development purposes. When deploying on your own instance, you'll need to replace `prod_auth_pub_key.pem` with the one matching your private key. The keys are currently configured on `config.edn` for the production environment, replacing it as an environment variable string should be enough.

## Running

To start a web server for the application, run:

    lein run

It'll run on port 3000. You'll need to have compiled ClojureScript first.

## Testing

Running `lein test` will run the tests against the `memento_test` database on the local host.  

If you're using Cursive Clojure, bear in mind it does not yet support a way to launch a REPL with specific environment profile. Since the application reads its database connection parameters from the environment configuration, if you start a REPL from Cursive and run the tests against it, you'll be running them against the development database and not the test one.

Make sure you either create a REPL profile specifically for the test settings, or just run the tests via lein.

## License

Copyright © 2015-2017 Numergent Limited.
