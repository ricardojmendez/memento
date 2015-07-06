java -XX:-MaxFDLimit -Ddatabase.url="postgresql://memento:testdb@localhost/memento_dev" -Dport=3333 -cp target/memento.jar clojure.main -m memento.core
