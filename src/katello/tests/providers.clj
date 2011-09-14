(ns katello.tests.providers
  (:refer-clojure :exclude [fn])
  (:require [katello.tasks :as tasks]
            [katello.httpclient :as http]
            [katello.api-tasks :as api]
            [clojure.java.io :as io])
  (:use [test.tree :only [fn data-driven]]
        [com.redhat.qe.verify :only [verify-that]]
        [com.redhat.qe.auto.bz :only [open-bz-bugs]]
        [katello.conf :only [config]]
        [katello.validation :only [field-validation expect-error duplicate-disallowed variations]]))

(def test-provider-name (atom nil))
(def test-product-name (atom nil))

(def create-custom 
  (fn [] (tasks/create-provider (tasks/uniqueify "auto-cp")
                               "my description"
                               :custom)))

(def rename
  (fn [] (let [old-name (tasks/uniqueify "rename")
              new-name (tasks/uniqueify "newname")]
          (tasks/create-provider old-name "my description" :custom)
          (tasks/edit-provider {:name old-name :new-name new-name})
          (let [current-providers (map :name (api/all-entities
                                              :provider
                                              "ACME_Corporation"))]
            (verify-that (and (some #{new-name} current-providers)
                              (not (some #{old-name} current-providers))))))))

(def delete
  (fn [] (let [provider-name (tasks/uniqueify "auto-provider-delete")]
          (tasks/create-provider provider-name
                                 "my description"
                                 :custom)
          (tasks/verify-success
           #(tasks/delete-provider provider-name)))))

(def setup-custom
  (fn [] (tasks/create-provider (reset! test-provider-name (tasks/uniqueify "cust"))
                               "my description" :custom)))

(def create-product
  (fn [] (tasks/add-product {:provider-name @test-provider-name
                            :name (reset! test-product-name (tasks/uniqueify "prod"))
                            :description "test product"})))

(def delete-product
  (fn [] (let [product {:provider-name @test-provider-name
                       :name (tasks/uniqueify "deleteme")
                       :description "test product to delete"}]
          (tasks/add-product product)
          (tasks/delete-product product))))

(def create-repo
  (fn [] (tasks/add-repo {:provider-name @test-provider-name
                         :product-name @test-product-name
                         :name (tasks/uniqueify "repo")
                         :url "http://test.com/myurl"})))

(def delete-repo
  (fn [] (let [repo {:name (tasks/uniqueify "deleteme")
                    :provider-name @test-provider-name
                    :product-name @test-product-name
                    :url "http://my.fake/url"}]
          (tasks/add-repo repo)
          (tasks/delete-repo repo))))

(def manifest-tmp-loc "/tmp/manifest.zip")
(def redhat-provider-name "Red Hat")
(def manifest-testing-blockers
  (fn [_]
    (if-not (-> (api/lookup-by :name redhat-provider-name :provider (@config :admin-org))
            :repository_url
            (.contains "example.com"))
      [:manifest-already-uploaded]
      [])))

(def manifest-setup
  (fn [] 
    (with-open [instream (io/input-stream (java.net.URL. (@config :redhat-manifest-url)))
                outstream (io/output-stream manifest-tmp-loc)]
      (io/copy instream outstream))))

(def upload-manifest
  (fn []
    (let [provider-name redhat-provider-name]
      (tasks/edit-provider {:name provider-name
                            :repo-url (@config :redhat-repo-url)})
      (tasks/upload-subscription-manifest {:provider-name provider-name
                                           :file-path manifest-tmp-loc}))))

(def dupe-disallowed
  (fn []
    (duplicate-disallowed tasks/create-provider [(tasks/uniqueify "dupe") "mydescription" :custom])))

(def validation
  (fn  [pred & [name description repo-url type :as args]]
    (field-validation tasks/create-provider args pred)))

(defn validation-data []
  (concat
   [[(expect-error :name-cant-be-blank) nil "blah" :redhat "http://sdf.com"]
                                
    ^{:blockers (open-bz-bugs "703528")
      :description "Test that invalid URL is rejected."}
    [(expect-error :repository-url-invalid) (tasks/uniqueify "mytestcp") "blah" :redhat "@$#%$%&%*()[]{}"]
    ^{:blockers (open-bz-bugs "703528")
      :description "Test that invalid URL is rejected."}
    [(expect-error :repository-url-invalid) (tasks/uniqueify "mytestcp") "blah" :redhat "https://"]
    [(expect-error :repository-url-invalid) (tasks/uniqueify "mytestcp") "blah" :redhat "@$#%$%&%*("]

    [(expect-error :repository-url-invalid) (tasks/uniqueify "mytestcp2") "blah" :redhat nil]
    [(expect-error :only-one-redhat-provider-per-org) (tasks/uniqueify "mytestcp3") nil :redhat "http://sdf.com"]
    [tasks/success? (tasks/uniqueify "mytestcp4") nil :custom "http://sdf.com"]]
   (variations
    [tasks/success? (tasks/uniqueify "mytestcp5") :javascript :custom "http://sdf.com"])
   (variations                  
    [(expect-error :name-no-leading-trailing-whitespace) :trailing-whitespace nil  :custom "http://sdf.com"])
   (variations
    [(expect-error :name-must-not-contain-characters) :invalid-character nil :custom "http://sdf.com"])))

