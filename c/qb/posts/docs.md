Title: Docs
Date: 2026-02-26
Tags: docs


## Quick Start

For installation, project layout, and running the system, refer to README.md.

## Componments

### Backend: Babashka as the runtime core
The backend runs entirely on [Bababhka](https://babashka.org), enabling a fast, single‑binary execution model. It integrates:
* [http-kit](https://github.com/http-kit/http-kit) - lightweight, built‑in HTTP server
* [integrant](https://github.com/weavejester/integrant) -declarative lifecycle management for system components
* [ruuter](https://github.com/askonomm/ruuter) - for routing HTTP requests
* [aero](https://github.com/juxt/aero) - environment‑aware configuration and profiles

This combination provides a minimal, scriptable, and highly composable backend architecture.

###  Frontend: Scittle‑based ClojureScript UI
The browser UI is powered by [Scittle](https://babashka.org/scittle/), allowing [ClojureScript](https://github.com/clojure/clojurescript) to run directly in the browser without a build pipeline. It includes:
* [reagent](http://reagent-project.github.io) - ClojureScript interface to [React](https://reactjs.org/) using [Hiccup](https://github.com/weavejester/hiccup) syntax
* [re-frame](https://github.com/day8/re-frame) - event‑driven state management
* [Bootstrap](https://getbootstrap.com) - responsive CSS framework
* [PrimeReact](https://primereact.org) - rich UI component library

This setup enables rapid iteration with zero‑build ClojureScript while still supporting a modern component ecosystem.

### Document Part

* [quickblog](https://github.com/borkdude/quickblog) - static site generation for documentation and content
