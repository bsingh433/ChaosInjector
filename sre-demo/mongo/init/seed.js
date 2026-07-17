// Runs once, on first container startup (empty /data/db).
// Creates the sampleapp database, the items collection, and a few seed docs.
db = db.getSiblingDB('sampleapp');

db.createCollection('items');
db.items.insertMany([
  { name: 'alpha', value: 1 },
  { name: 'beta', value: 2 },
  { name: 'gamma', value: 3 },
]);

print('[seed] sampleapp.items seeded with ' + db.items.countDocuments() + ' documents');
