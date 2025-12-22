# ⚡ LogicFlow

A **Prolog-like Domain Specific Language** implemented in Clojure, leveraging **Software Transactional Memory (STM)** for concurrent knowledge base management, with a beautiful **real-time web visualizer**.

![Clojure](https://img.shields.io/badge/Clojure-1.11+-5881D8?style=flat&logo=clojure&logoColor=white)
![ClojureScript](https://img.shields.io/badge/ClojureScript-1.11+-5881D8?style=flat&logo=clojure&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green.svg)

## ✨ Features

- 🧠 **Prolog-like DSL** - Define facts, rules, and queries using familiar logic programming syntax
- 🔄 **Unification Algorithm** - Pattern matching with logic variables
- 🔙 **Backtracking Search** - Lazy evaluation for efficient query resolution
- 🔒 **STM-based Knowledge Base** - Thread-safe concurrent access using Clojure's refs
- 🌐 **Real-time Web UI** - Beautiful visualizer with WebSocket updates
- 💻 **Browser REPL** - Execute Prolog queries directly in the browser
- 🌳 **Inference Tracing** - Visualize the inference tree step by step
- 📊 **Transaction History** - Track all knowledge base modifications
- 🔌 **REST API** - Full HTTP API for external integrations
- 📦 **Tabling/Memoization** - Prevent infinite recursion, improve performance
- 💾 **Persistence** - Save/load knowledge bases to EDN, JSON, or Prolog format

## 🚀 Quick Start

### Prerequisites

- Java 17+ (Java 21 recommended)
- [Clojure CLI tools](https://clojure.org/guides/getting_started)
- Node.js 16+ (for ClojureScript frontend)

### Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/logicflow.git
cd logicflow

# Make build script executable
chmod +x build.sh

# Check dependencies
./build.sh check

# Install dependencies
./build.sh deps
```

### Running the Application

```bash
# Development mode (hot-reload frontend + backend)
./build.sh dev

# Or run production build
./build.sh build
./build.sh run

# Start on custom port
./build.sh run 8080
```

Open http://localhost:3000 to access the web interface.

## 📖 Usage

### REPL-Driven Development

```clojure
;; Start a REPL
./build.sh repl

;; Load the core namespace
(require '[logicflow.core :refer :all])
(require '[logicflow.kb :as kb])
(require '[logicflow.builtins :as b])

;; Load example knowledge base
(load-family-example!)

;; Define facts
(deffact parent :tom :mary)
(deffact parent :mary :ann)

;; Define rules (Prolog syntax!)
(<- (grandparent ?x ?z)
    (parent ?x ?y)
    (parent ?y ?z))

(<- (ancestor ?x ?y)
    (parent ?x ?y))

(<- (ancestor ?x ?z)
    (parent ?x ?y)
    (ancestor ?y ?z))

;; Run queries
(query (grandparent ?who :ann))
;; => [{:who :tom}]

(query (ancestor :tom ?descendant))
;; => [{:descendant :mary} {:descendant :ann}]

;; Interactive query (prints results)
(?- (parent ?x ?y))

;; Show knowledge base
(show-kb)

;; Start web server
(require '[logicflow.web.server :as server])
(server/start! :port 3000)
```

### DSL Syntax

#### Facts

```clojure
;; Single fact
(deffact parent :tom :mary)

;; Multiple facts
(facts
  (parent :tom :mary)
  (parent :tom :bob)
  (parent :mary :ann))
```

#### Rules

```clojure
;; Arrow syntax (recommended - like Prolog!)
(<- (grandparent ?x ?z)
    (parent ?x ?y)
    (parent ?y ?z))

;; Named rule syntax
(defrule ancestor [?x ?y]
  (parent ?x ?y))

;; Recursive rules
(<- (ancestor ?x ?z)
    (parent ?x ?y)
    (ancestor ?y ?z))
```

#### Queries

```clojure
;; Find all solutions
(query (parent ?x ?y))

;; Find first solution
(query-first (parent ?x ?y))

;; Limit results
(query-n 5 (ancestor :tom ?who))

;; Multiple goals (conjunction)
(query (parent ?x ?y) (parent ?y ?z))
```

### Built-in Predicates

#### Arithmetic

```clojure
(require '[logicflow.builtins :as b])

;; X is Expr (like Prolog)
(b/is-goal ?sum '(+ 2 3))        ;; ?sum = 5
(b/is-goal ?prod '(* 4 5))       ;; ?prod = 20

;; Comparisons
(b/gt ?x ?y)    ;; >
(b/lt ?x ?y)    ;; <
(b/gte ?x ?y)   ;; >=
(b/lte ?x ?y)   ;; <=
(b/eq ?x ?y)    ;; =:=
(b/neq ?x ?y)   ;; =\=
```

#### Lists

```clojure
;; Membership
(b/membero ?x [:a :b :c])

;; Append
(b/appendo [1 2] [3 4] ?result)  ;; ?result = [1 2 3 4]

;; Length
(b/lengtho [:a :b :c] ?n)        ;; ?n = 3

;; Head/Tail
(b/firsto [1 2 3] ?h)            ;; ?h = 1
(b/resto [1 2 3] ?t)             ;; ?t = [2 3]
(b/conso ?h ?t [1 2 3])          ;; ?h = 1, ?t = [2 3]

;; Reverse
(b/reverseo [1 2 3] ?r)          ;; ?r = [3 2 1]
```

#### Type Checking

```clojure
(b/numbero ?x)    ;; Is number?
(b/atomo ?x)      ;; Is atom (keyword/symbol)?
(b/listo ?x)      ;; Is list?
(b/varo ?x)       ;; Is unbound variable?
(b/nonvaro ?x)    ;; Is bound?
(b/groundo ?x)    ;; Contains no variables?
```

## 🏗️ Architecture

```
logicflow/
├── src/logicflow/
│   ├── core.clj           # DSL macros (deffact, defrule, query)
│   ├── unify.clj          # Unification algorithm
│   ├── search.clj         # Backtracking search engine
│   ├── kb.clj             # STM knowledge base
│   ├── builtins.clj       # Built-in predicates (arithmetic, lists)
│   ├── trace.clj          # Inference tracing & debugging
│   ├── tabling.clj        # Memoization for recursive predicates
│   ├── persistence.clj    # Save/load KB to files
│   ├── examples.clj       # Example knowledge bases
│   ├── main.clj           # Application entry point
│   └── web/
│       ├── server.clj     # HTTP server (HTTP-Kit)
│       ├── api.clj        # REST API handlers
│       ├── ws.clj         # WebSocket handlers
│       └── ui.cljs        # Reagent/Re-frame frontend
├── resources/public/
│   ├── index.html
│   └── css/style.css
├── test/logicflow/
│   ├── unify_test.clj
│   ├── core_test.clj
│   └── integration_test.clj
├── build.sh               # Build script
├── deps.edn
└── shadow-cljs.edn
```

## 🔧 STM Features

LogicFlow uses Clojure's Software Transactional Memory for thread-safe knowledge base operations:

```clojure
;; All modifications are transactional
(kb/assert-fact! :parent [:tom :mary])    ; Uses dosync + alter
(kb/retract-fact! :parent [:tom :mary])   ; Atomic retraction

;; Concurrent reads are always consistent
(kb/get-all-facts)
(kb/get-all-rules)

;; Transaction history tracking
(kb/get-history)
```

## 🌐 REST API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/facts` | GET | Get all facts |
| `/api/facts` | POST | Add a fact |
| `/api/facts` | DELETE | Remove a fact |
| `/api/rules` | GET | Get all rules |
| `/api/rules` | POST | Add a rule |
| `/api/query` | POST | Execute a query |
| `/api/repl` | POST | Evaluate code (browser REPL) |
| `/api/trace` | GET | Get inference trace |
| `/api/history` | GET | Get transaction history |
| `/api/stats` | GET | Get KB statistics |
| `/api/clear` | POST | Clear knowledge base |
| `/api/load-example` | POST | Load example KB |
| `/api/export` | GET | Export KB |
| `/api/import` | POST | Import KB |
| `/api/save` | POST | Save KB to file |
| `/api/export-prolog` | GET | Export in Prolog format |

## 🧪 Testing

```bash
# Run all tests
./build.sh test

# Run specific test namespace
./build.sh test --focus logicflow.unify-test
```

## 📚 Examples

### Family Relations

```clojure
(load-family-example!)

;; Who are Tom's grandchildren?
(query (grandparent :tom ?grandchild))

;; Find all ancestor relationships
(query (ancestor ?x ?y))

;; Who is the mother of Ann?
(query (mother ?who :ann))
```

### Animal Expert System

```clojure
(require '[logicflow.examples :as ex])
(ex/load-animal-expert!)

;; Assert observed characteristics
(deffact has-hair :mystery)
(deffact eats-meat :mystery)
(deffact has-tawny-color :mystery)
(deffact has-dark-spots :mystery)

;; Identify the animal
(query (is-a :mystery ?type))
;; => [{:type :cheetah}]
```

### Graph Pathfinding

```clojure
(ex/load-graph-example!)

;; Find all paths from A to G
(query (path :a :g ?path ?cost))

;; Check reachability
(query (reachable :a :g))
```

## 🎨 Web Interface

The web interface provides:

- **Dashboard** - Overview of facts, rules, and statistics
- **Facts Browser** - View and manage facts by predicate
- **Rules Browser** - View and manage inference rules
- **Query Panel** - Interactive query execution with tracing
- **REPL** - Full Prolog REPL in the browser
- **Trace Viewer** - Visualize inference trees
- **History Timeline** - Transaction history visualization

## 🔮 Advanced Features

### Tabling (Memoization)

```clojure
(require '[logicflow.tabling :as t])

;; Enable tabling for recursive predicates
(t/deftabled fib [n result]
  (disj-all
    (conj-all (== n 0) (== result 0))
    (conj-all (== n 1) (== result 1))
    (fresh [n1 n2 r1 r2]
      (conj-all
        (is n1 (- n 1))
        (fib n1 r1)
        (is n2 (- n 2))
        (fib n2 r2)
        (is result (+ r1 r2))))))
```

### Inference Tracing

```clojure
(require '[logicflow.trace :as trace])

;; Enable tracing
(trace/with-tracing
  (query (ancestor :tom ?who)))

;; Get trace data
(trace/get-trace-log)
(trace/print-trace)
```

### Persistence

```clojure
(require '[logicflow.persistence :as p])

;; Save to EDN
(p/save-kb! "my-kb.edn")

;; Load from file
(p/load-kb! "my-kb.edn")

;; Export to Prolog format
(spit "kb.pl" (p/export-prolog))

;; Create backup
(p/create-backup!)
```

## 📄 Build Commands

```bash
./build.sh help        # Show all commands
./build.sh check       # Check dependencies
./build.sh deps        # Install dependencies
./build.sh clean       # Clean build artifacts
./build.sh build       # Full production build
./build.sh dev         # Development mode
./build.sh run         # Run server
./build.sh repl        # Start REPL
./build.sh watch       # Frontend watch mode
./build.sh test        # Run tests
./build.sh release     # Create release archive
```

## 📄 License

MIT License - see [LICENSE](LICENSE) for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 🙏 Acknowledgments

- Inspired by [core.logic](https://github.com/clojure/core.logic)
- Built with [Reagent](https://reagent-project.github.io/) and [Re-frame](https://day8.github.io/re-frame/)
- Styled with modern CSS and [JetBrains Mono](https://www.jetbrains.com/lp/mono/)
