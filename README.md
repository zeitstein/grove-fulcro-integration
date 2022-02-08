Proof of concept for using [Fulcro Raw](https://github.com/fulcrologic/fulcro)'s data management facilities with [grove](https://github.com/thheller/shadow-experiments)'s component framework.
Integration is mostly concerned with how to get data *out* of Fulcro DB to grove components.
To *mutate* the Fulcro db, use standard Fulcro tools (`transact!`, `defmutation`, Pathom, etc.)

WIP. Not tested. Kind of working, but this is the simplest implementation (using high-level Fulcro API). Not even a completely correct implementation (`hook-deps-update!`).

See:
- `dev.example-ui` to see it in action
- `dev.grove` for same UI but using grove's db (no Fulcro)
- `dev.custom` for explorations in making the integration more performant.

Thanks to [@thheller](https://github.com/thheller) and [@awkay](https://github.com/awkay) for their creations and discussions!
