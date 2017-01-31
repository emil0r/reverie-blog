# reverie-blog

blog module for reverie

## deps

```clojure
[reverie-blog "0.3.4"]
```
## Usage

In init.clj

```clojure
(load-view-ns 'reverie.modules.blog
              'reverie.apps.blog
              'reverie.endpoints.blog-feed
              'reverie.batteries.meta.module)

(reset! blog-feed/feed-content
            {:title "Example.com"
             :subtitle ""
             ;; unique id key. see settings.edn
             :id-key (settings/get settings [:feed :id-key])
             :url "https://www.example.com"
             :tagging-entity "example.com,2015"
             :blog-url "https://www.example.com/blog/"
             :rights "Copyright © Company Here"
             :generator "reverie/blog"})

(reset! apps.blog/blogger {:css {:image "any css class here for the image"}})
```

In settings.edn

```clojure
{ ;; before

  ;; UUIDv4 acts as a uniqiue key for the feed
  :feed {:id-key #uuid "0aa3e73c-be0a-4c30-900e-a83dac84e8fa"}
  
  ;; after
}
```

## License

Copyright © 2015-2017 Emil Bengtsson

--

Coram Deo
