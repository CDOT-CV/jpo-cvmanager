import { Subject } from 'rxjs'
import map_8801 from '../sample_data/intersection_8801_data/intersection_8801_MAP_data.json'
import spat_8801 from '../sample_data/intersection_8801_data/intersection_8801_SPAT_data.json'
import map_8802 from '../sample_data/intersection_8802_data/intersection_8802_MAP_data.json'
import spat_8802 from '../sample_data/intersection_8802_data/intersection_8802_SPAT_data.json'
import map_8803 from '../sample_data/intersection_8803_data/intersection_8803_MAP_data.json'
import spat_8803 from '../sample_data/intersection_8803_data/intersection_8803_SPAT_data.json'
import map_8804 from '../sample_data/intersection_8804_data/intersection_8804_MAP_data.json'
import spat_8804 from '../sample_data/intersection_8804_data/intersection_8804_SPAT_data.json'
import map_8805 from '../sample_data/intersection_8805_data/intersection_8805_MAP_data.json'
import spat_8805 from '../sample_data/intersection_8805_data/intersection_8805_SPAT_data.json'
import map_8806 from '../sample_data/intersection_8806_data/intersection_8806_MAP_data.json'
import spat_8806 from '../sample_data/intersection_8806_data/intersection_8806_SPAT_data.json'
import { MinimalClient } from './live-intersection-api'
import { sha256 } from 'js-sha256'

export const mockIntersectionList: IntersectionReferenceData[] = [
  {
    intersectionID: 8801,
    roadRegulatorID: -1,
    rsuIP: '10.11.81.29',
    longitude: -104.8863731,
    latitude: 39.5946919,
    intersectionName: null,
  },
  {
    intersectionID: 8802,
    roadRegulatorID: -1,
    rsuIP: '10.11.81.30',
    longitude: -104.8830946,
    latitude: 39.5948212,
    intersectionName: null,
  },
  {
    intersectionID: 8803,
    roadRegulatorID: -1,
    rsuIP: '10.11.81.31',
    longitude: -104.8808462,
    latitude: 39.5950352,
    intersectionName: null,
  },
  {
    intersectionID: 8804,
    roadRegulatorID: -1,
    rsuIP: '10.11.81.32',
    longitude: -104.8761659,
    latitude: 39.5950414,
    intersectionName: null,
  },
  {
    intersectionID: 8805,
    roadRegulatorID: -1,
    rsuIP: '10.11.81.33',
    longitude: -104.8667908,
    latitude: 39.5950548,
    intersectionName: null,
  },
  {
    intersectionID: 8806,
    roadRegulatorID: -1,
    rsuIP: '10.11.81.34',
    longitude: -104.8574648,
    latitude: 39.5950476,
    intersectionName: null,
  },
]

class FakeLiveDataApi {
  mapDataPeriodMs = 100
  spatDataPeriodMs = 100
  bsmDataPeriodMs = 100
  spatIndexes: { [key: number]: number } = {}
  bsmPositions: { [key: number]: { [key: number]: number[] } } = {}

  walkPoint = (point: number[]): number[] => {
    const [longitude, latitude] = point
    const newLongitude = longitude + (Math.random() - 0.5) * 0.0001
    const newLatitude = latitude + (Math.random() - 0.5) * 0.0001
    return [newLongitude, newLatitude]
  }

  startMockedMapData = (intersectionId: number, stream: Subject<liveMap>): MinimalClient => {
    let data = undefined as undefined | ProcessedMap[]
    switch (intersectionId) {
      case 8801:
        data = map_8801 as unknown as ProcessedMap[]
        break
      case 8802:
        data = map_8802 as unknown as ProcessedMap[]
        break
      case 8803:
        data = map_8803 as unknown as ProcessedMap[]
        break
      case 8804:
        data = map_8804 as unknown as ProcessedMap[]
        break
      case 8805:
        data = map_8805 as unknown as ProcessedMap[]
        break
      case 8806:
        data = map_8806 as unknown as ProcessedMap[]
        break
    }
    if (data === undefined) {
      return {
        disconnect: () => {},
        connect: () => {},
        subscribe: () => {},
      }
    }
    data.forEach((v) => {
      v.properties.odeReceivedAt = Date.now()
      v.properties.timeStamp = new Date().toISOString()
    })
    const intervalId = setInterval(() => {
      data.forEach((v) => {
        const mockData: liveMap = {
          type: 'map',
          rcv_ts: Date.now(),
          update_ts: Date.now(),
          payload: v,
          /* mock data content here */
        }

        // Push the data to the stream
        stream.next(mockData)
      })
    }, this.mapDataPeriodMs)
    // Generate data 10x every second
    return {
      disconnect: () => clearInterval(intervalId),
      connect: () => {},
      subscribe: () => {},
    }
  }

  startMockedSpatData = (intersectionId: number, stream: Subject<liveSpat>): MinimalClient => {
    let data = undefined as undefined | ProcessedSpat[]
    switch (intersectionId) {
      case 8801:
        data = spat_8801 as unknown as ProcessedSpat[]
        break
      case 8802:
        data = spat_8802 as unknown as ProcessedSpat[]
        break
      case 8803:
        data = spat_8803 as unknown as ProcessedSpat[]
        break
      case 8804:
        data = spat_8804 as unknown as ProcessedSpat[]
        break
      case 8805:
        data = spat_8805 as unknown as ProcessedSpat[]
        break
      case 8806:
        data = spat_8806 as unknown as ProcessedSpat[]
        break
    }
    if (data === undefined) {
      return {
        disconnect: () => {},
        connect: () => {},
        subscribe: () => {},
      }
    }
    data.forEach((v) => {
      v.odeReceivedAt = new Date().toISOString()
      v.utcTimeStamp = Date.now()
    })
    const intervalId = setInterval(() => {
      if (this.spatIndexes[intersectionId] == undefined) {
        this.spatIndexes[intersectionId] = 0
      }
      let currentSpatIndex = this.spatIndexes[intersectionId]
      const currentSpat = data[currentSpatIndex]
      const mockData: liveSpat = {
        type: 'spat',
        rcv_ts: Date.now(),
        update_ts: Date.now(),
        payload: currentSpat,
        /* mock data content here */
      }

      // Push the data to the stream
      stream.next(mockData)
      this.spatIndexes[intersectionId] = (currentSpatIndex + 1) % data.length
    }, this.spatDataPeriodMs)
    // Generate data 10x every second
    return {
      disconnect: () => clearInterval(intervalId),
      connect: () => {},
      subscribe: () => {},
    }
  }

  startMockedBsmData = (intersectionId: number, stream: Subject<liveBsm>): MinimalClient => {
    let initialPosition = undefined as undefined | number[]
    let mapMessage = undefined as undefined | ProcessedMap
    switch (intersectionId) {
      case 8801:
        mapMessage = map_8801[0] as unknown as ProcessedMap
        break
      case 8802:
        mapMessage = map_8802[0] as unknown as ProcessedMap
        break
      case 8803:
        mapMessage = map_8803[0] as unknown as ProcessedMap
        break
      case 8804:
        mapMessage = map_8804[0] as unknown as ProcessedMap
        break
      case 8805:
        mapMessage = map_8805[0] as unknown as ProcessedMap
        break
      case 8806:
        mapMessage = map_8806[0] as unknown as ProcessedMap
        break
    }
    if (mapMessage === undefined) {
      return {
        disconnect: () => {},
        connect: () => {},
        subscribe: () => {},
      }
    }
    initialPosition = [mapMessage.properties.refPoint.longitude, mapMessage.properties.refPoint.latitude]

    const intervalId = setInterval(() => {
      for (let i = 0; i < 10; i++) {
        // Combine intersection number and BSM ID to create a unique string
        // Generate a deterministic hash from the combined string
        const id = sha256(`${intersectionId}-${i}`)
        if (this.bsmPositions[intersectionId] == undefined) {
          this.bsmPositions[intersectionId] = {}
        }
        if (this.bsmPositions[intersectionId][id] == undefined) {
          this.bsmPositions[intersectionId][id] = [
            initialPosition[0] + (Math.random() - 0.5) * 0.001,
            initialPosition[1] + (Math.random() - 0.5) * 0.001,
          ]
        }
        const newPosition = this.walkPoint(this.bsmPositions[intersectionId][id])
        const mockBsm: liveBsm = {
          type: 'bsm',
          rcv_ts: Date.now(),
          update_ts: Date.now(),
          payload: this.mockBsm(id, newPosition, Math.random() * 100, Math.random() * 360, new Date()),
          /* mock data content here */
        }
        stream.next(mockBsm)
      }
    }, this.bsmDataPeriodMs)
    // Generate data 10x every second
    return {
      disconnect: () => clearInterval(intervalId),
      connect: () => {},
      subscribe: () => {},
    }
  }

  getMillisecondsOfMinute = (date: Date): number => {
    const seconds = date.getSeconds()
    const milliseconds = date.getMilliseconds()
    return seconds * 1000 + milliseconds
  }

  mockBsm = (id: string, location: number[], speed: number, heading: number, ts: Date): BsmFeature => {
    return {
      type: 'Feature',
      properties: {
        msgCnt: 0,
        id: id,
        secMark: this.getMillisecondsOfMinute(ts),
        position: {
          latitude: location[1],
          longitude: location[0],
          elevation: 0,
        },
        accelSet: {
          accelLat: null,
          accelLong: null,
          accelVert: null,
          accelYaw: null,
        },
        accuracy: { semiMajor: 2, semiMinor: 2, orientation: 0 },
        transmission: 'UNAVAILABLE',
        speed: speed,
        heading: heading,
        angle: null,
        brakes: {
          wheelBrakes: {
            leftFront: false,
            rightFront: false,
            unavailable: true,
            leftRear: false,
            rightRear: false,
          },
          traction: 'unavailable',
          abs: 'off',
          scs: 'unavailable',
          brakeBoost: 'unavailable',
          auxBrakes: 'unavailable',
        },
        size: { width: 180, length: 480 },
        odeReceivedAt: ts.getTime(),
      },
      geometry: {
        type: 'Point',
        coordinates: location,
      },
    }
  }
}

export default new FakeLiveDataApi()
