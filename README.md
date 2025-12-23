# Лабораторная работа №4

---

**Выполнили:** Барашко Арсений и Пасечник Иван  

**Группа:** Р3331  

**Преподаватель:** Пенской Александр Владимирович  

**Язык:** Clojure / ClojureScript

---

## Цель

Получить навыки работы с метапрограммированием, макросами, DSL и реализацией интерпретаторов.

---

## Задание

Реализовать DSL для логического программирования в стиле Prolog с:

- Унификацией и поиском с возвратом (backtracking)
- STM-based базой знаний (потокобезопасный доступ)
- Веб-визуализатором для отладки запросов

### Требования

- Prolog-подобный синтаксис через макросы Clojure
- Алгоритм унификации с occurs check
- Ленивый поиск решений с backtracking
- STM (refs, atoms, agents) для управления состоянием
- Real-time веб-интерфейс с WebSocket
- Трассировка вывода для визуализации

---

## Архитектура

```
+---------------------------+
|      DSL Macros           |  <- core.clj (deffact, defrule, query)
+---------------------------+
            |
            | AST (quoted forms)
            v
+-----------------------+      +------------------------------+
| Unification Engine    |<-----| Knowledge Base (STM)         |
| (unify.clj)           |      | refs: facts, rules, history  |
+-----------------------+      +------------------------------+
            |
            | substitutions
            v
+------------------------+
| Backtracking Search    |  <- search.clj (lazy sequences)
+------------------------+
            |
            | solutions stream
            v
+------------------------+      +------------------------------+
| REST API / WebSocket   |<---->| Web Visualizer (Reagent)     |
| (server.clj, api.clj)  |      | (ui.cljs)                    |
+------------------------+      +------------------------------+
```

### Алгоритм унификации

```
unify(t1, t2, subs):
  t1' = walk(t1, subs)
  t2' = walk(t2, subs)
  
  if t1' == t2':           return subs
  if lvar?(t1'):           return extend(t1', t2', subs)
  if lvar?(t2'):           return extend(t2', t1', subs)
  if seq?(t1') && seq?(t2'):
    return unify-seq(t1', t2', subs)
  else:                    return FAIL
```

---

## Структура проекта

```
LogicFlow/
├── deps.edn                    # Зависимости Clojure
├── shadow-cljs.edn             # Конфигурация ClojureScript
├── package.json                # npm зависимости
├── build.sh                    # Скрипт сборки
├── README.md
├── src/logicflow/
│   ├── core.clj                # DSL макросы (deffact, defrule, query)
│   ├── unify.clj               # Алгоритм унификации
│   ├── search.clj              # Backtracking search
│   ├── kb.clj                  # STM Knowledge Base
│   ├── trace.clj               # Трассировка вывода
│   ├── builtins.clj            # Встроенные предикаты
│   ├── tabling.clj             # Мемоизация (tabling)
│   ├── persistence.clj         # Сохранение/загрузка KB
│   ├── examples.clj            # Примеры баз знаний
│   ├── main.clj                # Entry point
│   └── web/
│       ├── server.clj          # HTTP-Kit сервер
│       ├── api.clj             # REST API
│       ├── ws.clj              # WebSocket handler
│       └── ui.cljs             # ClojureScript UI (Reagent)
├── test/logicflow/
│   ├── unify_test.clj
│   ├── core_test.clj
│   └── integration_test.clj
├── resources/public/
│   ├── index.html
│   └── css/style.css
└── .github/workflows/
    └── ci.yml
```

---

## Реализация алгоритмов

### Унификация (`unify.clj`)

Унификация — сопоставление двух термов с нахождением подстановки переменных.

Для термов $t_1$ и $t_2$ находим подстановку $\theta$ такую, что $t_1\theta = t_2\theta$.

```clojure
(defn unify
  [term1 term2 subs]
  (let [t1 (walk term1 subs)
        t2 (walk term2 subs)]
    (cond
      (= t1 t2) subs
      (lvar? t1) (extend-subs t1 t2 subs)
      (lvar? t2) (extend-subs t2 t1 subs)
      (and (sequential? t1) (sequential? t2))
      (unify-seq t1 t2 subs)
      :else nil)))
```

**Особенности:**
- Occurs check для предотвращения бесконечных структур
- Поддержка вложенных структур (списки, мапы)
- Walk для разрешения цепочек переменных

---

### Backtracking Search (`search.clj`)

Поиск с возвратом реализован через ленивые последовательности:

$$\text{solve}(G_1, G_2, ..., G_n) = \bigcup_{\theta \in G_1} \text{solve}(G_2\theta, ..., G_n\theta)$$

```clojure
(defn conj-goals
  [goal1 goal2]
  (fn [subs]
    (mapcat goal2 (goal1 subs))))

(defn disj-goals
  [goal1 goal2]
  (fn [subs]
    (lazy-cat (goal1 subs) (goal2 subs))))

(defn solve
  ([goals] (solve goals empty-subs))
  ([goals subs]
   (if (empty? goals)
     [subs]
     (let [[g & gs] goals]
       (mapcat #(solve gs %) (g subs))))))
```

**Особенности:**
- Ленивые последовательности для эффективного перебора
- Комбинаторы `conj-goals` (AND) и `disj-goals` (OR)
- Поддержка cut и negation-as-failure

---

### STM Knowledge Base (`kb.clj`)

База знаний использует Clojure STM для потокобезопасного доступа:

```clojure
(def facts  (ref {}))   ; {:predicate -> #{[args...]}}
(def rules  (ref {}))   ; {:predicate -> [{:head :body}]}
(def history (ref []))  ; transaction log

(defn assert-fact!
  [predicate args]
  (dosync
   (alter facts update predicate (fnil conj #{}) args)
   (alter history conj {:type :assert
                        :predicate predicate
                        :args args
                        :timestamp (System/currentTimeMillis)})))
```

**Особенности:**
- `ref` + `dosync` для транзакционных изменений
- `atom` для кэширования и статистики
- `agent` для асинхронных уведомлений
- Watchers для real-time обновлений UI

---

### DSL Макросы (`core.clj`)

Макросы для Prolog-подобного синтаксиса:

```clojure
(defmacro deffact
  [predicate & args]
  `(kb/assert-fact! ~(keyword predicate) ~(vec args)))

(defmacro defrule
  [name head & body]
  `(kb/add-rule! ~(keyword name) '~head '~(vec body)))

(defmacro <-
  [head & body]
  `(kb/add-rule! ~(keyword (first head)) 
                 '~(vec (rest head)) 
                 '~(vec body)))

(defmacro query
  [& goals]
  `(kb/query '~(vec goals)))
```

**Пример использования:**

```clojure
;; Факты
(deffact parent :tom :mary)
(deffact parent :mary :ann)

;; Правило
(<- (grandparent ?x ?z)
    (parent ?x ?y)
    (parent ?y ?z))

;; Запрос
(query (grandparent ?who :ann))
;; => ({:who :tom})
```

---

## Веб-интерфейс

### REST API

| Метод | Endpoint | Описание |
|-------|----------|----------|
| GET | `/api/facts` | Получить все факты |
| POST | `/api/facts` | Добавить факт |
| GET | `/api/rules` | Получить все правила |
| POST | `/api/query` | Выполнить запрос |
| GET | `/api/history` | История транзакций |
| POST | `/api/load-example` | Загрузить пример |

### WebSocket

Real-time обновления через WebSocket `/ws`:

```clojure
{:type :fact-added, :predicate :parent, :args [:tom :mary]}
{:type :query-executed, :goals [...], :result-count 3}
{:type :kb-cleared}
```

---

## Запуск

```bash
# Установка зависимостей
./build.sh deps

# Запуск (frontend + backend)
./build.sh start

# Только backend
./build.sh run

# Только frontend
./build.sh watch

# Тесты
./build.sh test
```

Откройте http://localhost:3000 в браузере.

---

## Пример работы

```clojure
;; Определение семейных отношений
(deffact parent :tom :mary)
(deffact parent :tom :bob)
(deffact parent :mary :ann)
(deffact male :tom)
(deffact female :mary)

;; Правила
(<- (father ?x ?y) (parent ?x ?y) (male ?x))
(<- (grandparent ?x ?z) (parent ?x ?y) (parent ?y ?z))

;; Запросы
(query (parent ?x ?y))
;; => ({:x :tom, :y :mary} {:x :tom, :y :bob} {:x :mary, :y :ann})

(query (grandparent ?who :ann))
;; => ({:who :tom})

(query (father ?f ?c))
;; => ({:f :tom, :c :mary} {:f :tom, :c :bob})
```

---

## Выводы

В данной лабораторной работе я:

- Реализовал DSL для логического программирования с унификацией и backtracking
- Применил метапрограммирование через макросы Clojure для создания Prolog-подобного синтаксиса
- Использовал STM (refs, atoms, agents) для потокобезопасной базы знаний
- Создал веб-визуализатор с real-time обновлениями через WebSocket
- Реализовал трассировку вывода для отладки запросов

**Ключевые приёмы программирования:**

| Приём | Где использован |
|-------|-----------------|
| Макросы | `deffact`, `defrule`, `<-`, `query` |
| Ленивые последовательности | `solve`, `disj-goals`, rule resolution |
| STM (refs) | Knowledge Base: `facts`, `rules`, `history` |
| Рекурсия | `unify`, `walk`, `symbolize-term` |
| Функции высшего порядка | `conj-goals`, `disj-goals`, `trace-goal` |
| Pattern matching | Унификация термов |
| ClojureScript + Reagent | Веб-интерфейс |
| WebSocket | Real-time обновления |

**Реализованные возможности Prolog:**

- ✅ Факты и правила
- ✅ Унификация с occurs check
- ✅ Backtracking (поиск с возвратом)
- ✅ Логические переменные
- ✅ Составные термы
- ✅ Negation as failure
- ✅ Cut (отсечение)
- ✅ Tabling (мемоизация)
- ✅ Арифметика (`is/2`)
- ✅ Сравнения (`>`, `<`, `>=`, `<=`)
- ✅ Операции со списками (`member`, `append`, `length`)

---
