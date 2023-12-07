(ns quil.test-util
  (:import java.awt.GraphicsEnvironment)
  (:require [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.test :as t]))

(defn- display-density []
  (try
    (.. GraphicsEnvironment (getLocalGraphicsEnvironment) (getDefaultScreenDevice) (getScaleFactor))
    (catch IllegalArgumentException e
      ; getScaleFactor() method is present only on osx.
      ; For other systems return 1 for now.
      1)))

(defn path-to-snippet-snapshots [platform]
  (str "dev-resources/snippet-snapshots/"
       platform
       (if (= 2 (display-density))
         "/retina/"
         "/normal/")))

(defn expected-image [platform test-name]
  (str (path-to-snippet-snapshots platform)
       test-name
       "-expected.png"))

(defn actual-image [platform test-name]
  (str (path-to-snippet-snapshots platform)
       test-name
       "-actual.png"))

(defn diff-image [platform test-name]
  (str (path-to-snippet-snapshots platform)
       test-name
       "-difference.png"))

(defn compare-images
  "Compares images at file paths `expected` and `actual` and produces another
  image at path `difference` which highlights any differences in the images.

  Returns a number between `0` and `1` indicating a measure of the difference,
  with `0` indicating the images are the same, and `nil` if imagemagick not
  installed."
  [expected actual difference]
  ;; use imagemagick compare executable for comparison
  ;; see https://imagemagick.org/script/compare.php
  (let [{:keys [err]} (sh/sh "compare" "-metric" "mae" expected actual difference)
        result        (second (re-find #"\((.*)\)" err))]
    (if result
      (Double/parseDouble result)
      (do
        (println "Couldn't parse output of compare. Got following string: " err)
        1.0))))

(defn assert-match-reference! [test-name platform actual-file]
  (let [expected-file (expected-image platform test-name)
        diff-file (diff-image platform test-name)
        result (compare-images expected-file actual-file diff-file)
        ;; identify output to verify image sizes are equivalent
        identify (:out (sh/sh "identify" actual-file expected-file))
        threshold 0.02]
    (when (number? result)
      (if (<= result threshold)
        (do
          (io/delete-file (io/file actual-file))
          (io/delete-file (io/file diff-file)))
        ;; add actual and expected images to difference image for easier comparison
        (sh/sh "convert"
               actual-file
               diff-file
               expected-file
               "+append"
               diff-file))
      (t/is (<= result threshold)
            (str "Image differences in \"" test-name "\", see: " diff-file "\n"
                 identify)))))
