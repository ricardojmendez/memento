# memento

Work in progress, pre-alpha experimental project.

## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

You will also need a Elastic Search instance running locally on the standard ports, with a cluster called *memento*.

## Running

To start a web server for the application, run:

    lein ring server

## Testing

Running `lein test` will run the tests against an index `memento-test` on the local host.  

If you're using Cursive Clojure, bear in mind it does not yet support a way to launch a REPL with specific environment profile. Since the application reads its database connection parameters from the environment configuration, if you start a REPL from Cursive and run the tests against it, you'll be running them against the development database and not the test one.

Make sure you either create a REPL profile specifically for the test settings, or just run the tests via lein.

## License

Copyright © 2015 Numergent Limited.
