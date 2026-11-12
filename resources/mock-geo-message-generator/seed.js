const denverCoordinates = [
  [-104.9903, 39.7392],
  [-104.9915, 39.7421],
  [-104.9892, 39.7455],
  [-104.9970, 39.7484],
  [-104.9803, 39.7520],
  [-104.9870, 39.7332],
  [-104.9927, 39.7294],
  [-104.9860, 39.7591],
  [-104.9810, 39.7694],
  [-104.9990, 39.7745],
  [-104.9750, 39.7448],
  [-105.0065, 39.7361],
];

const targetDatabase = db.getSiblingDB(seedDatabaseName);
const now = Math.floor(Date.now() / 60_000) * 60_000;

function createFeature(messageType, index) {
  const id = `CV-MOCK-${messageType}-${String(index + 1).padStart(2, '0')}`;
  const timestamp = new Date(now - (denverCoordinates.length - index) * 60_000)
    .toISOString()
    .replace(/\.000Z$/, 'Z');
  const properties = {
    id,
    timeStamp: timestamp,
    schemaVersion: 2,
    messageType,
    speed: 8 + index,
    heading: (index * 31) % 360,
    mockData: true,
  };

  if (messageType === 'BSM') {
    properties.vehicleType = 'passengerVehicle';
  } else {
    properties.basicType = 'personal';
    properties.personalDeviceUserType = 'pedestrian';
  }

  return {
    _id: id,
    type: 'Feature',
    geometry: {
      type: 'Point',
      coordinates: denverCoordinates[index],
    },
    properties,
    recordGeneratedAt: timestamp,
  };
}

function upsertMessages(messageType, collectionName) {
  const operations = denverCoordinates.map((_, index) => {
    const feature = createFeature(messageType, index);
    return {
      replaceOne: {
        filter: { _id: feature._id },
        replacement: feature,
        upsert: true,
      },
    };
  });
  const result = targetDatabase.getCollection(collectionName).bulkWrite(operations);
  print(`Upserted ${operations.length} mock ${messageType} features into ${collectionName}`);
  return result;
}

upsertMessages('BSM', seedBsmCollection);
upsertMessages('PSM', seedPsmCollection);
