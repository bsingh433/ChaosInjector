# mongo

MongoDB for the demo's sample app. Backs the `/items` CRUD endpoints.

- **Image:** extends `mongo:7`; `init/seed.js` creates the `sampleapp` database,
  the `items` collection, and seed documents on first startup.
- **Port:** 27017.
- **Persistence:** in the overall compose, `/data/db` is a named volume, so data
  survives restarts (the seed only runs when the data dir is empty).

## Build & run standalone
```bash
docker build -t sre-demo-mongo .
docker run --rm -p 27017:27017 -v sampleapp-mongo:/data/db sre-demo-mongo
```

Inspect:
```bash
mongosh mongodb://localhost:27017/sampleapp --eval "db.items.find()"
```

## Config (env)
- `MONGO_INITDB_ROOT_USERNAME` / `MONGO_INITDB_ROOT_PASSWORD` — set both to run
  with auth (demo runs without auth by default).
