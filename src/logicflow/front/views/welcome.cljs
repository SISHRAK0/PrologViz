(ns logicflow.front.views.welcome
  "Welcome view - comprehensive documentation and tutorial for LogicFlow."
  (:require [re-frame.core :as rf]))

(def colors
  {:bg-primary "#0a0e14"
   :bg-secondary "#0d1117"
   :bg-tertiary "#151b23"
   :bg-elevated "#1c2333"
   :accent "#00d4aa"
   :accent-dim "rgba(0, 212, 170, 0.1)"
   :text-primary "#e6edf3"
   :text-secondary "#8b949e"
   :text-muted "#484f58"
   :border "#21262d"
   :success "#10b981"
   :error "#ef4444"
   :warning "#fbbf24"
   :purple "#a78bfa"
   :pink "#ff6b9d"})

(defn highlight-code
  "Подсвечивает комментарии серым цветом"
  [code]
  (let [lines (clojure.string/split code #"\n")]
    (into [:span]
          (interpose "\n"
                     (for [line lines]
                       (if (clojure.string/starts-with? (clojure.string/trim line) ";;")
                         [:span {:style {:color "#6a737d"}} line]
                         [:span {:style {:color "#00d4aa"}} line]))))))

(defn code-block [code]
  [:pre {:style {:background (:bg-secondary colors)
                 :border (str "1px solid " (:border colors))
                 :border-radius "8px"
                 :padding "16px"
                 :margin "12px 0"
                 :overflow-x "auto"
                 :font-family "'JetBrains Mono', monospace"
                 :font-size "13px"
                 :line-height "1.6"}}
   [highlight-code code]])

(defn code-inline [code]
  [:code {:style {:background (:bg-elevated colors)
                  :padding "2px 8px"
                  :border-radius "4px"
                  :font-family "'JetBrains Mono', monospace"
                  :font-size "0.9em"
                  :color (:accent colors)}}
   code])

(defn section-card [title icon children]
  [:div {:style {:background (:bg-tertiary colors)
                 :border (str "1px solid " (:border colors))
                 :border-radius "12px"
                 :padding "24px"
                 :margin-bottom "24px"}}
   [:h3 {:style {:font-size "1.4rem"
                 :font-weight "600"
                 :color (:text-primary colors)
                 :margin-bottom "20px"
                 :padding-bottom "12px"
                 :border-bottom (str "2px solid " (:accent colors))
                 :display "flex"
                 :align-items "center"
                 :gap "10px"}}
    [:span icon]
    title]
   children])

(defn info-box [type content]
  (let [border-color (case type
                       :info (:accent colors)
                       :warning (:warning colors)
                       :success (:success colors)
                       :error (:error colors)
                       (:accent colors))]
    [:div {:style {:background (:bg-secondary colors)
                   :border-left (str "4px solid " border-color)
                   :border-radius "4px"
                   :padding "16px"
                   :margin "16px 0"}}
     content]))

(defn intro-section []
  [:div {:style {:text-align "center"
                 :padding "48px 24px"
                 :margin-bottom "32px"
                 :background (str "linear-gradient(135deg, " (:bg-tertiary colors) " 0%, " (:bg-secondary colors) " 100%)")
                 :border-radius "16px"
                 :border (str "1px solid " (:border colors))}}
   [:h1 {:style {:font-size "3rem"
                 :font-weight "700"
                 :background "linear-gradient(135deg, #00d4aa, #00ffcc)"
                 :background-clip "text"
                 :-webkit-background-clip "text"
                 :-webkit-text-fill-color "transparent"
                 :margin-bottom "12px"}}
    "⚡ LogicFlow"]
   [:p {:style {:font-size "1.25rem"
                :color (:text-secondary colors)
                :margin-bottom "32px"}}
    "Декларативный язык логического программирования"]
   [:div {:style {:display "flex"
                  :justify-content "center"
                  :gap "16px"
                  :flex-wrap "wrap"}}
    (for [[icon text] [["🧠" "Логический вывод"]
                       ["🔄" "Backtracking"]
                       ["🎯" "Унификация"]
                       ["📊" "Визуализация"]]]
      ^{:key text}
      [:div {:style {:display "flex"
                     :align-items "center"
                     :gap "8px"
                     :padding "12px 20px"
                     :background (:bg-secondary colors)
                     :border-radius "24px"
                     :border (str "1px solid " (:border colors))}}
       [:span {:style {:font-size "1.25rem"}} icon]
       [:span {:style {:color (:text-secondary colors) :font-weight "500"}} text]])]])

(defn what-is-logic-programming []
  [section-card "Что такое логическое программирование?" "🤔"
   [:div
    [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "16px"}}
     "Представь, что ты разговариваешь с очень умным другом. Ты говоришь ему факты о мире, "
     "а потом задаёшь вопросы — и он сам находит ответы, используя логику."]
    [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "16px"}}
     [:strong {:style {:color (:accent colors)}} "Обычное программирование: "]
     "ты пишешь " [:em "как"] " решить задачу (алгоритм, шаги)."]
    [:p {:style {:color (:text-secondary colors) :line-height "1.8"}}
     [:strong {:style {:color (:accent colors)}} "Логическое программирование: "]
     "ты описываешь " [:em "что"] " ты знаешь, а компьютер сам находит решение."]]])

(defn facts-section []
  [section-card "Факты — кирпичики знаний" "📦"
   [:div
    [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "16px"}}
     [:strong {:style {:color (:accent colors)}} "Факт"] 
     " — это простое утверждение, которое ты считаешь истинным."]
    [:h4 {:style {:color (:accent colors) :margin "20px 0 12px"}} "Структура факта"]
    [code-block "(deffact предикат аргумент1 аргумент2 ...)"]
    [:h4 {:style {:color (:accent colors) :margin "20px 0 12px"}} "Примеры"]
    [code-block ";; \"Москва — столица России\"\n(deffact capital :russia :moscow)\n\n;; \"Алиса любит пиццу\"\n(deffact likes :alice :pizza)\n\n;; \"Иван — родитель Марии\"\n(deffact parent :ivan :maria)"]]])

(defn rules-section []
  [section-card "Правила — логические связи" "⚙️"
   [:div
    [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "16px"}}
     [:strong {:style {:color (:accent colors)}} "Правило"] 
     " — это способ вывести новые факты из существующих. Формула: \"ЕСЛИ ... ТО ...\"."]
    [:h4 {:style {:color (:accent colors) :margin "20px 0 12px"}} "Структура правила"]
    [code-block "(<- (голова ?переменные...)\n    (условие1 ?переменные...)\n    (условие2 ?переменные...))"]
    [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "16px"}}
     "Читается: " [:strong "\"Голова истинна, ЕСЛИ все условия истинны\"."]]
    [:h4 {:style {:color (:accent colors) :margin "20px 0 12px"}} "Переменные"]
    [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "12px"}}
     "Переменные начинаются с " [code-inline "?"] " и могут принимать любое значение."]
    [:h4 {:style {:color (:accent colors) :margin "20px 0 12px"}} "Пример"]
    [code-block ";; \"X — дедушка Z, если X — родитель Y, и Y — родитель Z\"\n(<- (grandparent ?x ?z)\n    (parent ?x ?y)\n    (parent ?y ?z))"]]])

(defn queries-section []
  [section-card "Запросы — задаём вопросы" "🔍"
   [:div
    [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "16px"}}
     [:strong {:style {:color (:accent colors)}} "Запрос"] 
     " — это вопрос к базе знаний. Система ищет все подходящие ответы."]
    [:h4 {:style {:color (:accent colors) :margin "20px 0 12px"}} "Структура запроса"]
    [code-block "(query (цель1 ?переменные...)\n       (цель2 ?переменные...)\n       ...)"]
    [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "16px"}}
     "Если целей несколько — все они должны быть выполнены (логическое И)."]
    [:h4 {:style {:color (:accent colors) :margin "20px 0 12px"}} "Простые примеры"]
    [code-block ";; Кто любит пиццу?\n(query (likes ?who :pizza))\n\n;; Что любит Алиса?\n(query (likes :alice ?what))"]
    [:h4 {:style {:color (:accent colors) :margin "20px 0 12px"}} "Сложный пример"]
    [code-block ";; Найти людей, которые любят здоровую и доступную еду\n(query (likes ?person ?food)\n       (healthy ?food)\n       (available ?food))\n\n;; Система найдёт ВСЕ комбинации, удовлетворяющие ВСЕМ условиям"]]])

(defn search-mechanism-section []
  [section-card "Как работает поиск" "🔮"
   [:div
    [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "16px"}}
     "Два ключевых процесса: "
     [:strong {:style {:color (:accent colors)}} "унификация"] " и "
     [:strong {:style {:color (:accent colors)}} "backtracking"] "."]
    
    [:div {:style {:background (:bg-secondary colors)
                   :border (str "1px solid " (:border colors))
                   :border-radius "8px"
                   :padding "20px"
                   :margin "20px 0"}}
     [:h4 {:style {:color (:purple colors) :margin-bottom "12px" :font-size "1.2rem"}} 
      "🎯 Унификация — сопоставление с образцом"]
     [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "16px"}}
      "Система ищет такие значения переменных, чтобы выражения стали одинаковыми."]
     [:div {:style {:background (:bg-primary colors)
                    :border-radius "6px"
                    :padding "12px"
                    :font-family "'JetBrains Mono', monospace"
                    :font-size "13px"}}
      [:div {:style {:margin-bottom "8px"}}
       [:span {:style {:color (:text-muted colors)}} "Запрос: "]
       [:span {:style {:color (:accent colors)}} "(likes ?who :pizza)"]]
      [:div {:style {:margin-bottom "8px"}}
       [:span {:style {:color (:text-muted colors)}} "Факт:  "]
       [:span {:style {:color (:pink colors)}} "(likes :alice :pizza)"]]
      [:div
       [:span {:style {:color (:text-muted colors)}} "Результат: "]
       [:span {:style {:color (:success colors)}} "?who = :alice ✓"]]]]
    
    [:div {:style {:background (:bg-secondary colors)
                   :border (str "1px solid " (:border colors))
                   :border-radius "8px"
                   :padding "20px"
                   :margin "20px 0"}}
     [:h4 {:style {:color (:warning colors) :margin-bottom "12px" :font-size "1.2rem"}} 
      "🔄 Backtracking — поиск с возвратом"]
     [:p {:style {:color (:text-secondary colors) :line-height "1.8" :margin-bottom "16px"}}
      "Система пробует варианты, и если тупик — возвращается и пробует другой путь."]
     [:div {:style {:background (:bg-primary colors)
                    :border-radius "6px"
                    :padding "16px"
                    :color (:text-secondary colors)
                    :line-height "1.8"}}
      [:p "1. Развилка с тремя путями"]
      [:p "2. Первый путь → тупик!"]
      [:p "3. " [:strong {:style {:color (:warning colors)}} "Возврат"] " → второй путь → тупик!"]
      [:p "4. " [:strong {:style {:color (:warning colors)}} "Возврат"] " → третий путь → " [:strong {:style {:color (:success colors)}} "выход!"]]]]
    
    [info-box :success
     [:div
      [:strong {:style {:color (:success colors)}} "Главное: "]
      [:span {:style {:color (:text-secondary colors)}}
       "Система автоматически перебирает ВСЕ комбинации. Тебе не нужно писать циклы!"]]]]])

(defn examples-section []
  [section-card "Примеры использования" "💡"
   [:div
    [:div {:style {:background (:bg-secondary colors)
                   :border (str "1px solid " (:border colors))
                   :border-radius "8px"
                   :padding "20px"
                   :margin-bottom "20px"}}
     [:h4 {:style {:color (:accent colors) :margin-bottom "12px"}} "🌍 География — столицы и континенты"]
     [:p {:style {:color (:text-secondary colors) :margin-bottom "12px"}}
      "База знаний о странах с правилом для поиска европейских столиц."]
     [code-block ";; === ФАКТЫ: что мы знаем о мире ===\n\n;; Столицы стран (страна -> город)\n(deffact capital :russia :moscow)\n(deffact capital :france :paris)\n(deffact capital :japan :tokyo)\n\n;; На каком континенте находится страна\n(deffact continent :russia :europe)\n(deffact continent :france :europe)\n(deffact continent :japan :asia)\n\n;; === ПРАВИЛО: выводим новые знания ===\n\n;; Город является европейской столицей, ЕСЛИ:\n;; 1) это столица какой-то страны\n;; 2) эта страна находится в Европе\n(<- (european-capital ?city)\n    (capital ?country ?city)      ; ?city — столица ?country\n    (continent ?country :europe)) ; ?country в Европе\n\n;; === ЗАПРОС: задаём вопрос ===\n\n(query (european-capital ?city))\n;; Результат: [{:city :moscow} {:city :paris}]\n;; Токио не попал — Япония не в Европе!"]]
    
    [:div {:style {:background (:bg-secondary colors)
                   :border (str "1px solid " (:border colors))
                   :border-radius "8px"
                   :padding "20px"
                   :margin-bottom "20px"}}
     [:h4 {:style {:color (:purple colors) :margin-bottom "12px"}} "🔗 Графы — поиск путей"]
     [:p {:style {:color (:text-secondary colors) :margin-bottom "12px"}}
      "Классическая задача — найти все достижимые узлы в графе."]
     [code-block ";; === ФАКТЫ: рёбра графа ===\n;; Граф: A → B → C → D\n\n(deffact edge :a :b)  ; из A можно попасть в B\n(deffact edge :b :c)  ; из B можно попасть в C  \n(deffact edge :c :d)  ; из C можно попасть в D\n\n;; === ПРАВИЛА: определяем \"путь\" ===\n\n;; Базовый случай: если есть ребро — есть путь\n(<- (path ?x ?y)\n    (edge ?x ?y))\n\n;; Рекурсивный случай: путь через промежуточный узел\n;; Из X можно попасть в Z, если:\n;; 1) из X есть ребро в Y\n;; 2) из Y есть путь в Z\n(<- (path ?x ?z)\n    (edge ?x ?y)   ; X → Y (прямое ребро)\n    (path ?y ?z))  ; Y → ... → Z (рекурсивно)\n\n;; === ЗАПРОС ===\n\n(query (path :a ?where))  ; Куда можно попасть из A?\n;; Результат: :b, :c, :d (все узлы достижимы!)"]]
    
    [:div {:style {:background (:bg-secondary colors)
                   :border (str "1px solid " (:border colors))
                   :border-radius "8px"
                   :padding "20px"}}
     [:h4 {:style {:color (:warning colors) :margin-bottom "12px"}} "♛ N-Queens Problem — задача о ферзях"]
     [:p {:style {:color (:text-secondary colors) :margin-bottom "12px"}}
      "Классическая головоломка: расставить N ферзей на шахматной доске NxN так, чтобы ни один ферзь не бил другого."]
     
     [:div {:style {:background (:bg-primary colors)
                    :border-radius "6px"
                    :padding "16px"
                    :margin-bottom "16px"
                    :color (:text-secondary colors)}}
      [:p {:style {:margin-bottom "8px"}} [:strong "Как это работает:"] ]
      [:p {:style {:margin-bottom "4px"}} "• Доска 4x4 имеет 4 столбца"]
      [:p {:style {:margin-bottom "4px"}} "• В каждом столбце ставим ровно одного ферзя"]
      [:p {:style {:margin-bottom "4px"}} "• Решение [2, 4, 1, 3] означает:"]
      [:p {:style {:margin-left "16px" :margin-bottom "4px"}} "  - столбец 1: ферзь на строке 2"]
      [:p {:style {:margin-left "16px" :margin-bottom "4px"}} "  - столбец 2: ферзь на строке 4"]
      [:p {:style {:margin-left "16px" :margin-bottom "4px"}} "  - столбец 3: ферзь на строке 1"]
      [:p {:style {:margin-left "16px"}} "  - столбец 4: ферзь на строке 3"]]
     
     [code-block ";; === ИДЕЯ РЕШЕНИЯ ===\n;; Перебираем все перестановки чисел [1,2,3,4]\n;; Каждая перестановка — это расстановка ферзей\n;; Проверяем, что ферзи не бьют друг друга по диагонали\n;; (по горизонтали и вертикали уже не бьют — разные строки и столбцы)\n\n;; Проверка: ферзь на позиции Q1 не атакует ферзя на Q2\n;; если они не на одной диагонали\n(<- (no-diagonal-attack ?q1 ?q2 ?distance)\n    (!= ?q1 (+ ?q2 ?distance))   ;; не на диагонали вправо-вверх\n    (!= ?q1 (- ?q2 ?distance)))  ;; не на диагонали вправо-вниз\n\n;; Главное правило: найти безопасную перестановку\n(<- (queens ?n ?solution)\n    (permutation (range 1 ?n) ?solution)\n    (all-safe ?solution))\n\n;; Запрос: найти все решения для доски 4x4\n(query (queens 4 ?solution))\n;; Результат: [2 4 1 3], [3 1 4 2]"]
     [info-box :info
      [:span {:style {:color (:text-secondary colors)}}
       "Попробуйте интерактивную визуализацию на вкладке Puzzles!"]]]]])

(defn builtin-table [title color rows]
  [:div {:style {:margin-bottom "24px"}}
   [:h4 {:style {:color color :margin-bottom "12px"}} title]
   [:table {:style {:width "100%" :border-collapse "collapse" :font-size "0.9rem"}}
    [:thead
     [:tr {:style {:background (:bg-secondary colors)}}
      [:th {:style {:text-align "left" :padding "12px" :color (:text-muted colors) :border-bottom (str "1px solid " (:border colors))}} "Предикат"]
      [:th {:style {:text-align "left" :padding "12px" :color (:text-muted colors) :border-bottom (str "1px solid " (:border colors))}} "Описание"]
      [:th {:style {:text-align "left" :padding "12px" :color (:text-muted colors) :border-bottom (str "1px solid " (:border colors))}} "Пример"]]]
    [:tbody
     (for [[pred desc example] rows]
       ^{:key pred}
       [:tr {:style {:border-bottom (str "1px solid " (:border colors))}}
        [:td {:style {:padding "12px" :font-family "'JetBrains Mono', monospace" :color (:accent colors) :font-size "0.85rem"}} pred]
        [:td {:style {:padding "12px" :color (:text-secondary colors)}} desc]
        [:td {:style {:padding "12px" :font-family "'JetBrains Mono', monospace" :color (:text-primary colors) :font-size "0.85rem"}} example]])]]])

(defn builtins-section []
  [section-card "Встроенные предикаты" "🔧"
   [:div
    [builtin-table "🔢 Арифметика" (:warning colors)
     [["(is ?x expr)" "Вычисляет выражение" "(is ?sum (+ 2 3))"]
      ["(> ?x ?y)" "X больше Y" "(> 10 5)"]
      ["(< ?x ?y)" "X меньше Y" "(< 3 10)"]
      ["(plus ?x ?y ?z)" "Z = X + Y" "(plus 2 3 ?z)"]
      ["(between ?a ?b ?x)" "X от A до B" "(between 1 3 ?n)"]]]
    
    [builtin-table "📋 Списки" (:purple colors)
     [["(member ?x ?list)" "X — элемент списка" "(member ?x [1 2 3])"]
      ["(append ?l1 ?l2 ?l3)" "L3 = L1 + L2" "(append [1] [2] ?r)"]
      ["(length ?list ?n)" "N — длина списка" "(length [a b c] ?n)"]
      ["(first ?list ?x)" "X — первый элемент" "(first [1 2 3] ?h)"]
      ["(reverse ?l ?r)" "R — обратный список" "(reverse [1 2] ?r)"]]]
    
    [builtin-table "🏷️ Типы" (:pink colors)
     [["(number ?x)" "X — число" "(number 42)"]
      ["(atom ?x)" "X — атом" "(atom :hello)"]
      ["(list ?x)" "X — список" "(list [1 2])"]
      ["(var ?x)" "X — свободная переменная" "(var ?unknown)"]]]
    
    [builtin-table "🎛️ Управление" (:success colors)
     [["(true)" "Всегда успешен" "(true)"]
      ["(fail)" "Всегда неуспешен" "(fail)"]
      ["(once ?goal)" "Только первое решение" "(once (path ?x ?y))"]]]]])

(defn interface-section []
  [section-card "Интерфейс приложения" "🖥️"
   [:div {:style {:display "grid"
                  :grid-template-columns "repeat(auto-fit, minmax(220px, 1fr))"
                  :gap "16px"}}
    (for [[view icon title desc] [[:dashboard "📊" "Dashboard" "Обзор базы знаний, статистика, быстрые действия"]
                                   [:facts "📝" "Facts" "Просмотр и управление фактами в базе знаний"]
                                   [:rules "⚙️" "Rules" "Просмотр определённых правил и их структуры"]
                                   [:query "🔍" "Query" "Выполнение запросов к базе знаний"]
                                   [:repl "💻" "REPL" "Интерактивная консоль для Clojure кода"]
                                   [:trace "🌳" "Trace" "Визуализация дерева поиска и backtracking"]
                                   [:spy "🔎" "Spy" "Отладка: точки наблюдения на предикатах"]
                                   [:puzzles "🧩" "Puzzles" "Головоломки: N-Queens, Sudoku, Einstein"]]]
      ^{:key view}
      [:div {:style {:background (:bg-secondary colors)
                     :border (str "1px solid " (:border colors))
                     :border-radius "12px"
                     :padding "20px"
                     :cursor "pointer"
                     :transition "all 0.2s ease"}
             :on-click #(rf/dispatch [:set-view view])
             :on-mouse-over (fn [e] (set! (.. e -currentTarget -style -borderColor) "#00d4aa")
                                    (set! (.. e -currentTarget -style -transform) "translateY(-2px)"))
             :on-mouse-out (fn [e] (set! (.. e -currentTarget -style -borderColor) "#21262d")
                                   (set! (.. e -currentTarget -style -transform) "translateY(0)"))}
       [:div {:style {:display "flex" :align-items "center" :gap "10px" :margin-bottom "8px"}}
        [:span {:style {:font-size "1.5rem"}} icon]
        [:h4 {:style {:color (:accent colors) :margin "0" :font-size "1.1rem" :font-family "'Outfit', sans-serif"}} title]]
       [:p {:style {:color (:text-secondary colors) :font-size "0.9rem" :margin "0" :line-height "1.5" :font-family "'Outfit', sans-serif"}} desc]])]])

(defn get-started-section []
  [:div {:style {:text-align "center"
                 :padding "32px"
                 :background (str "linear-gradient(135deg, " (:accent-dim colors) " 0%, " (:bg-tertiary colors) " 100%)")
                 :border-radius "12px"
                 :border "1px solid rgba(0, 212, 170, 0.3)"
                 :margin-top "24px"}}
   [:h3 {:style {:font-size "1.5rem" :color (:text-primary colors) :margin-bottom "12px"}}
    "🚀 Начать работу"]
   [:p {:style {:color (:text-secondary colors) :margin-bottom "24px"}}
    "Выберите готовый пример на Dashboard или создайте свою базу знаний в REPL"]
   [:div {:style {:display "flex" :justify-content "center" :gap "16px" :flex-wrap "wrap"}}
    [:button {:style {:padding "14px 28px"
                      :font-size "1rem"
                      :font-weight "500"
                      :border "none"
                      :border-radius "8px"
                      :cursor "pointer"
                      :background "linear-gradient(135deg, #00d4aa, #00b894)"
                      :color "#0a0e14"}
              :on-click #(rf/dispatch [:set-view :dashboard])}
     "📊 Dashboard"]
    [:button {:style {:padding "14px 28px"
                      :font-size "1rem"
                      :font-weight "500"
                      :border (str "1px solid " (:border colors))
                      :border-radius "8px"
                      :cursor "pointer"
                      :background (:bg-secondary colors)
                      :color (:text-primary colors)}
              :on-click #(rf/dispatch [:set-view :repl])}
     "💻 REPL"]]])

(defn welcome-view []
  [:div {:style {:max-width "1000px" :margin "0 auto" :padding "24px"}}
   [intro-section]
   [what-is-logic-programming]
   [facts-section]
   [rules-section]
   [queries-section]
   [search-mechanism-section]
   [examples-section]
   [builtins-section]
   [interface-section]
   [get-started-section]])