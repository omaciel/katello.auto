(ns katello.notifications
  (:require [clojure.data.json :as json]
            [katello.locators :as locators]
            [com.redhat.qe.auto.selenium.selenium 
              :refer [browser loop-with-timeout]] 
            [slingshot.slingshot :refer [throw+ try+]]
            [tools.verify :refer [verify-that]]
            [clojure.set :refer [union]])
  (:import [com.thoughtworks.selenium SeleniumException]))

;;
;; Notifications
;;

(def notice-array-js-var "window.notices.noticeArray")

(def ^{:doc "All the different validation error messages that Katello
             can throw. The keys are keywords that can be used to
             refer to this type of error, and the values are regexes
             that match the error notification message in the UI."}
  validation-errors
  (let [errors {::name-taken-error                    #"(Username|Name) has already been taken"
                ::sg-name-taken-error                 #"Name must be unique within one organization"
                ::name-no-leading-trailing-whitespace #"Name must not contain leading or trailing white space"
                ::name-must-not-contain-characters    #"Name cannot contain characters other than"
                ::name-must-be-unique-within-org      #"Name must be unique within one organization" 
                ::repository-url-invalid              #"Repository url is invalid"
                ::start-date-time-cant-be-blank       #"Date and Time can't be blank"
                ::password-too-short                  #"Password must be at least"
                ::password-same-as-username           #"(Password|Username)" ;TODO after bug (open-bz-bugs "841499") is fixed add real notification
                ::repository-url-cant-be-blank        #"Repository url can't be blank"
                ::name-cant-be-blank                  #"Name can't be blank"
                ::max-systems-must-be-positive        #"Max systems must be a positive"
                ::max-systems-may-not-be-zero         #"Max systems may not be set to 0"}]
    
    (doseq [e (keys errors)]
      (derive e ::validation-error))  ; validation-error is a parent type
                                      ; so you can catch that type to
                                      ; mean "any" validation error.
    errors))

(def ^{:doc "A mapping of known errors in Katello. This helps
  automation throw and catch the right type of exception interally,
  taking UI error messages and mapping them to internal error types."}
  known-errors
  (let [errors {::invalid-credentials                   #"incorrect username"
                ::promotion-already-in-progress         #"Cannot promote.*while another changeset"
                ::import-older-than-existing-data       #"Import is older than existing data"
                ::distributor-has-already-been-imported #"This distributor has already been imported by another owner"}]
    (doseq [e (conj (keys errors) ::validation-error)]
      (derive e ::katello-error))
    (merge errors validation-errors)))

(defn matching-errors
  "Returns a set of matching known errors"
  [notifSet]
  (->> known-errors
     (filter (fn [[_ v]] (some not-empty (for [msg (map :msg notifSet)] (re-find v msg)))))
     (map key)
     set))

(def success?
  "Returns true if the given notification is a 'success' type
  notification (aka green notification in the UI)."
  ^{:type :serializable.fn/serializable-fn
    :serializable.fn/source 'success?}
  (fn [notif]
    (and notif (-> notif :type (= :success)))))

(def no-error?
  "Returns true if the given notification is a 'success' or 'notification' type
  notification (aka green notification in the UI)."
  ^{:type :serializable.fn/serializable-fn
    :serializable.fn/source 'success?}
  (fn [notif]
    (and notif (some #{(:type notif)} [:success :message]))))


(defn wait-for-notification-gone
  "Waits for a notification to disappear within the timeout period. If no
   notification is present, the function returns immediately. The default
   timeout is 3 seconds."
  [ & [max-wait-ms]]
  (loop-with-timeout (or max-wait-ms 3000) []
    (if ( browser isElementPresent :notification)
      (do (Thread/sleep 100) (recur)))
    (throw+ {:type :wait-for-notification-gone-timeout} 
            "Notification did not disappear within the specified timeout")))

(defn notifications
  "Gets all notifications from the page, returns a list of maps
   representing the notifications. Waits for timeout-ms for at least
   one notification to appear. Does not do any extra waiting after the
   first notification is detected. Default timeout is 15 seconds."
  [ & [{:keys [timeout-ms] :or {timeout-ms 2000}}]]
  (try
    (loop-with-timeout timeout-ms []
      (let [noticeArray (->> notice-array-js-var
                             (format "JSON.stringify(%s)") 
                             (browser getEval)
                             json/read-json)]
        (if (empty? noticeArray) 
          (recur)
          (for [notice noticeArray] 
            (assoc notice :type (keyword (:level notice)) 
                          :msg (str (:validationErrors notice) (:notices notice)))))))
    (catch SeleniumException e '())))

(defn check-for-success
  "Returns information about a success notification from the UI. Will
   wait for a success notification until timeout occurs, collecting
   any failure notifications captured in that time. If there are no
   notifications or any failure notifications are captured, an
   exception is thrown containing information about all captured
   notifications (including a success notification if present).
   Otherwise return the type and text of the message. Takes an
   optional max amount of time to wait, in ms, and whether to refresh
   the page periodically while waiting for a notification."
  [ & [{:keys [timeout-ms refresh?] :or {timeout-ms 2000}}]]
  (loop-with-timeout timeout-ms []
    (let [new-notifs (set (notifications
                           {:timeout-ms (if refresh? 15000 timeout-ms)}))]
      (cond (every? no-error? new-notifs) new-notifs
            (empty? new-notifs) (do (when refresh? (browser refresh)) (recur))
            :else (throw+ {:types (matching-errors new-notifs) :notifications new-notifs})))))

(defn check-for-error
  "Waits for a notification up to the optional timeout (in ms), throws
  an exception if error notification appears."
  [ & [{:keys [timeout-ms] :as m}]]
  (try+ (check-for-success m)
        (catch [:type ::no-success-message-error] _)))

(defn verify-success
  "Calls task-fn and checks for a success message afterwards. If none
   is found, or error notifications appear, throws an exception."
  [task-fn]
  (let [notifications (task-fn)]
    (verify-that (every? success? notifications))))

