(ns memento.misc.cljs-macros)

(defmacro adapt-bootstrap
  "Adapts a react bootstrap class for use in reagent"
  [x]
  (list
    'def x
    (list 'reagent/adapt-react-class (symbol (str "js/ReactBootstrap." x)))))

