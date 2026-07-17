# MongoDB for the sample app.
# The official image runs any *.js / *.sh in /docker-entrypoint-initdb.d/ on the
# FIRST start (empty data dir), so seed.js creates the DB, collection, and seed docs.
FROM mongo:7

COPY init/seed.js /docker-entrypoint-initdb.d/seed.js

EXPOSE 27017
