java -XX:-MaxFDLimit -Ddatabase.url="jdbc:postgresql://localhost/memento_dev?user=memento&password=testdb" -Dport=3333 -cp target/memento.jar clojure.main -m memento.core
