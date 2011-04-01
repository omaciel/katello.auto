(ns kalpana.locators
  (:use [com.redhat.qe.auto.selenium.selenium :only [SeleniumLocatable browser]]
        [com.redhat.qe.auto.navigate :only [page]]
        [com.redhat.qe.config :only [same-name]]
        [clojure.contrib.string :only [capitalize]])
  (:import [com.redhat.qe.auto.selenium Element LocatorTemplate]))

;;ui layer

(defmacro define-strategies
  "Create a function for each locator strategy in map m (which maps
  symbol to LocatorStrategy). Each function will be named the same as
  the symbol, take arguments and return a new element constructed
  with the locator strategy and args."
  [m]
  `(do ~@(for [loc-strat (keys m)]
           `(defn ~loc-strat [& args#]
              (Element. ~(m loc-strat) (into-array args#))))))

(define-strategies
  {link (LocatorTemplate. "" "link=$1")
   tab (LocatorTemplate. "Tab" "link=$1")
   environment-link (LocatorTemplate. "Environment" "//div[@id='main']//ul//a[.='$1']")
   org-link (LocatorTemplate. "Organization" "//div[@id='main']//ul//a[.='$1']")
   cp-link (LocatorTemplate. "Content Provider" "//div[@id='provider_list']//a[.='$1']")
   textbox (LocatorTemplate. "" "xpath=//*[self::input[(@type='text' or @type='password') and @name='$1'] or self::textarea[@name='$1']]")
   env-breadcrumb-link (LocatorTemplate. "Environment Breadcrumb" "//div[@id='content_envs']//a[.='$1']")
   promotion-content-category (LocatorTemplate. "Content Category" "//div[@id='left_accordion']//a[.='$1']")
   promotion-add-content-item (LocatorTemplate. "Add Content Item" "//div[@id='left_accordion']//li[normalize-space(.)='$1 Add']//a[normalize-space(.)='Add']")
   promotion-remove-content-item (LocatorTemplate. "Remove Content Item" "//div[@id='left_accordion']//li[normalize-space(.)='$1 Remove']//a[normalize-space(.)='Remove']")})

(defn- tabs "creates mapping eg: {:my-tab 'link=My Tab'}"
  [keys]
  (same-name capitalize tab keys))

(def uimap (merge
            {:error-message "//div[@class='warning']"
             :success-message "//div[@class='success']"

             ;; login page
             :username-text (textbox "username")
             :password-text (textbox "password")
             :log-in "commit"

             ;;main banner
             :search-textbox (textbox "s")
             :search-button "//button[@form='s']"
             :log-out "//a[normalize-space(.)='Logout']"
             
             ;;tabs with special chars in name
             :sub-organizations (tab "Sub-Organizations")

             ;;Organizations tab
             :new-organization (link "New Organization")
             :create-organization "//input[@name='commit' and @value='Create']"
             :org-name-text (textbox "name")
             :org-description-text (textbox "description")
             :org-environments (link "Environments")
             :edit-organization (link "Edit")
             :delete-organization (link "Delete")

             ;;Environments tab
             :env-name-text (textbox "name")
             :env-description-text (textbox "description")
             :prior-environment "//select[@id='environment_prior']"
             :create-environment "//input[@name='commit' and @value='Create']"
             :new-environment (link "New Environment")
             :delete-environment (link "Delete")
             :edit-environment (link "Edit")

             ;;Content Management tab
             :add-content-provider "//input[@type='submit' and @value='Add']"
             :cp-name-text (textbox "kalpana_model_provider[name]")
             :cp-description-text (textbox "kalpana_model_provider[description]")
             :cp-repository-url-text (textbox "kalpana_model_provider[repository_url]")
             :cp-type-list  "name=kalpana_model_provider[provider_type]"
             :cp-username-text (textbox "kalpana_model_provider[login_credential_attributes][username]")
             :cp-password-text (textbox "kalpana_model_provider[login_credential_attributes][password]")
             :cp-create-save "//input[@name='commit' and @value='Save']"
             :remove-content-provider "//input[@value='Remove']"
             :subscriptions (link "Subscriptions")
             :choose-file "//input[@type='file' and @id='kalpana_model_provider_contents']"
             :upload "//input[@value='Upload']"
             ;;Promotions subtab
             :products-category (promotion-content-category "Products")
             :errata-category (promotion-content-category "Errata")
             :packages-category (promotion-content-category "Packages")
             :kickstart-trees-category (promotion-content-category "Kickstart Trees")
             :promote-to-next-environment "//input[starts-with(@value,'Promote to')]"
             }
             
            ;;regularly named tabs
            (tabs [:organizations
                   :administration
                   :systems
                   :content-management
                   :dashboard
                   :environments
                   :subscriptions
                   :create

                   ;;subtabs
                   :content-providers
                   :sync-management
                   :promotions
                   ])))

(extend-protocol SeleniumLocatable
  clojure.lang.Keyword
  (sel-locator [k] (uimap k)))

;;page layout

(def page-tree
  (page :top-level (fn [] (if-not (browser isElementPresent :log-out) (browser open "/")))
        (page :content-management-tab (fn [] (browser clickAndWait :content-management))
              (page :content-providers-tab (fn [] (browser clickAndWait :content-providers))
                    (page :new-content-provider-page
                          (fn [] (browser clickAndWait :add-content-provider)))
                    (page :named-content-provider-page
                          (fn [cp-name] (browser clickAndWait (cp-link cp-name)))))
              (page :promotions-page (fn [] (browser clickAndWait :promotions))
                    (page :named-environment-promotions-page (fn [env-name] (browser clickAndWait (env-breadcrumb-link env-name))))))
        (page :organizations-tab (fn [] (browser clickAndWait :organizations))
              (page :new-organization-page (fn [] (browser clickAndWait :new-organization)))
              (page :named-organization-page (fn [org-name] (browser clickAndWait (org-link org-name)))
                    (page :edit-organization-page
                          (fn [] (browser clickAndWait :edit-organization)))
                    (page :org-environments-page
                          (fn [] (browser clickAndWait :org-environments))
                          (page :new-environment-page
                                (fn [] (browser clickAndWait :new-environment)))
                          (page :named-environment-page
                                (fn [env-name] (browser clickAndWait (environment-link env-name)))
                                (page :edit-environment-page
                                      (fn [] (browser clickAndWait :edit-environment)))))))))


