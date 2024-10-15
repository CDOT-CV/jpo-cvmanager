import { merge, Observable, Subject, Subscription } from 'rxjs'

class LiveIntersectionApi {
  // A map of subjects to simulate streams of data for different intersections
  dataStreams: {
    maps: { [key: number]: Subject<liveMap> }
    spats: { [key: number]: Subject<liveSpat> }
    bsms: { [key: string]: Subject<liveBsm> }
  } = { maps: {}, spats: {}, bsms: {} }

  subscriptions: {
    maps: { [key: number]: NodeJS.Timeout }
    spats: { [key: number]: NodeJS.Timeout }
    bsms: { [key: string]: NodeJS.Timeout }
  } = { maps: {}, spats: {}, bsms: {} }

  bsmPositions: { [key: string]: number[] } = {}

  denverBounds = [
    [-105.34749101080337, -104.39850345918389],
    [39.40100251198231, 40.080578520640984],
  ]

  getRandomPointInDenver = (): number[] => {
    const [west, east] = this.denverBounds[0]
    const [south, north] = this.denverBounds[1]
    const longitude = Math.random() * (east - west) + west
    const latitude = Math.random() * (north - south) + south
    return [longitude, latitude]
  }

  startMockedMapData = (intersectionId: number) => {
    if (!this.dataStreams.maps[intersectionId]) {
      this.dataStreams.maps[intersectionId] = new Subject<liveMap>()
    }
    if (this.subscriptions.maps[intersectionId]) {
      clearInterval(this.subscriptions.maps[intersectionId])
    }

    this.subscriptions.maps[intersectionId] = setInterval(() => {
      const mockData: liveMap = {
        type: 'map',
        rcv_ts: Date.now(),
        update_ts: Date.now(),
        payload: null,
        /* mock data content here */
      }

      // Push the data to the stream
      this.dataStreams.maps[intersectionId].next(mockData)
    }, 100)
    // Generate data 10x every second
  }

  startMockedSpatData = (intersectionId: number) => {
    if (!this.dataStreams.spats[intersectionId]) {
      this.dataStreams.spats[intersectionId] = new Subject<liveSpat>()
    }
    if (this.subscriptions.spats[intersectionId]) {
      clearInterval(this.subscriptions.spats[intersectionId])
    }

    this.subscriptions.spats[intersectionId] = setInterval(() => {
      const mockData: liveSpat = {
        type: 'spat',
        rcv_ts: Date.now(),
        update_ts: Date.now(),
        payload: null,
        /* mock data content here */
      }

      // Push the data to the stream
      this.dataStreams.spats[intersectionId].next(mockData)
    }, 100) // Generate data 10x every second
  }

  startMockedBsmData = (vehicleId: string, period_ms: number) => {
    if (!this.dataStreams.bsms[vehicleId]) {
      this.dataStreams.bsms[vehicleId] = new Subject<liveBsm>()
    }
    if (this.subscriptions.bsms[vehicleId]) {
      clearInterval(this.subscriptions.maps[vehicleId])
    }
    // generate random unique id

    this.subscriptions.bsms[vehicleId] = setInterval(() => {
      const lastPosition: number[] = this.bsmPositions[vehicleId] ?? this.getRandomPointInDenver()
      const walkedPosition = [lastPosition[0] + Math.random() * 0.000001, lastPosition[1] + Math.random() * 0.000001]
      const mockData: liveBsm = {
        type: 'bsm',
        rcv_ts: Date.now(),
        update_ts: Date.now(),
        payload: this.mockBsm(vehicleId, walkedPosition, Math.random() * 100, Math.random() * 360, new Date()),
        /* mock data content here */
      }

      // Push the data to the stream
      this.dataStreams.bsms[vehicleId].next(mockData)
    }, period_ms)
  }

  stopMockedMapData = (intersectionId: string) => {
    if (this.dataStreams.maps[intersectionId]) {
      this.dataStreams.maps[intersectionId].complete()
      delete this.dataStreams.maps[intersectionId]
    }
    if (this.subscriptions.maps[intersectionId]) {
      clearInterval(this.subscriptions.maps[intersectionId])
    }
  }

  stopMockedSpatData = (intersectionId: string) => {
    if (this.dataStreams.spats[intersectionId]) {
      this.dataStreams.spats[intersectionId].complete()
      delete this.dataStreams.spats[intersectionId]
    }
    if (this.subscriptions.spats[intersectionId]) {
      clearInterval(this.subscriptions.spats[intersectionId])
    }
  }

  stopMockedBsmData = (vehicleId: string) => {
    if (this.dataStreams.bsms[vehicleId]) {
      this.dataStreams.bsms[vehicleId].complete()
      delete this.dataStreams.bsms[vehicleId]
    }
    if (this.subscriptions.bsms[vehicleId]) {
      clearInterval(this.subscriptions.bsms[vehicleId])
    }
  }

  stopAllMockedData = () => {
    for (const key in this.dataStreams.maps) {
      this.stopMockedMapData(key)
    }
    for (const key in this.dataStreams.spats) {
      this.stopMockedSpatData(key)
    }
    for (const key in this.dataStreams.bsms) {
      this.stopMockedBsmData(key)
    }
  }

  getMapStream = (intersectionId: number): Observable<liveMap> => {
    return this.dataStreams.maps[intersectionId].asObservable()
  }

  getSpatStream = (intersectionId: number): Observable<liveSpat> => {
    return this.dataStreams.spats[intersectionId].asObservable()
  }

  getBsmStream = (vehicleId: string): Observable<liveBsm> => {
    return this.dataStreams.bsms[vehicleId].asObservable()
  }

  public subscribeToAllStreams(callback: (data: any) => Subscription) {
    const allStreams = [
      ...Object.values(this.dataStreams.maps),
      ...Object.values(this.dataStreams.spats),
      ...Object.values(this.dataStreams.bsms),
    ]

    const mergedStream = merge(...allStreams)

    return mergedStream.subscribe(callback)
  }

  public subscribeToAllBsms(callback: (data: liveBsm) => void): Subscription {
    const allStreams = Object.values(this.dataStreams.bsms)
    const mergedStream = merge(...allStreams)
    return mergedStream.subscribe(callback)
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

export default new LiveIntersectionApi()
