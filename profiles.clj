{:profiles/dev  {:env {:dev          true
                       :database-url "postgres://memento:testdb@localhost/memento_dev"
                       :auth-conf    {:passphrase "testpassword"
                                      :pubkey     "keys/dev_auth_pubkey.pem"
                                      :privkey    "keys/dev_auth_privkey.pem"}
                       }}
 :profiles/test {:env {:dev          true
                       :database-url "postgres://memento:testdb@localhost/memento_test"
                       :auth-conf    {:passphrase "testpassword"
                                      :pubkey     "keys/dev_auth_pubkey.pem"
                                      :privkey    "keys/dev_auth_privkey.pem"}
                       }}}
