import { IMessage, Stomp } from '@stomp/stompjs'
import { merge, Observable, Subject, Subscription } from 'rxjs'
import FakeLiveDataApi from './fake-data-api'
import mapboxgl from 'mapbox-gl'
import { cloneDeep } from 'lodash'

export interface MinimalClient {
  connect: (headers: unknown, connectCallback: () => void, errorCallback?: (error: string) => void) => void
  subscribe: (destination: string, callback: (message: IMessage) => void) => void
  disconnect: (disconnectCallback?: () => void) => void
}

const hslToHex = (h: number, s: number, l: number): string => {
  l /= 100
  const a = (s * Math.min(l, 1 - l)) / 100
  const f = (n: number) => {
    const k = (n + h / 30) % 12
    const color = l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1)
    return Math.round(255 * color)
      .toString(16)
      .padStart(2, '0') // Convert to hex and pad with zeroes
  }
  return `#${f(0)}${f(8)}${f(4)}`
}

export const getBsmColor = (bsm?: BsmFeature): string => {
  const id = bsm?.properties.id
  if (id === undefined || id === null) return '#585858'

  // Convert the id to a string to ensure consistent hashing
  const idString = id.toString()

  // Deterministically map id to a random color
  const hash = idString.split('').reduce((acc, char) => char.charCodeAt(0) + acc, 0)
  const hue = hash % 360

  const hexColor = hslToHex(hue, 100, 50)

  return hexColor
}

const createWebsocketConnection = (
  token: string,
  url: string,
  intersectionId: number,
  roadRegulatorId: number,
  numRestarts: number = 0
): {
  client: MinimalClient
  mapStream: Subject<liveMap>
  spatStream: Subject<liveSpat>
  bsmStream: Subject<liveBsm>
} => {
  let protocols = ['v10.stomp', 'v11.stomp']
  protocols.push(token)
  console.debug('Connecting to STOMP endpoint: ' + url + ' with token: ' + token)

  // Stomp Client Documentation: https://stomp-js.github.io/stomp-websocket/codo/extra/docs-src/Usage.md.html
  let client = Stomp.client(url, protocols)
  client.debug = (e) => {
    console.debug('STOMP Debug: ' + e)
  }

  // Topics are in the format /live/{roadRegulatorID}/{intersectionID}/{spat,map,bsm}
  let spatTopic = `/live/${roadRegulatorId}/${intersectionId}/spat`
  let mapTopic = `/live/${roadRegulatorId}/${intersectionId}/map`
  let bsmTopic = `/live/${roadRegulatorId}/${intersectionId}/bsm` // TODO: Filter by road regulator ID

  const mapStream = new Subject<liveMap>()
  const spatStream = new Subject<liveSpat>()
  const bsmStream = new Subject<liveBsm>()

  client.connect(
    {},
    () => {
      client.subscribe(mapTopic, function (mes: IMessage) {
        const message: ProcessedMap = JSON.parse(mes.body)
        const ts = Date.now()
        mapStream.next({ type: 'map', rcv_ts: ts, update_ts: ts, payload: message })
      })
      client.subscribe(spatTopic, function (mes: IMessage) {
        const message: ProcessedSpat = JSON.parse(mes.body)
        const ts = Date.now()
        spatStream.next({ type: 'spat', rcv_ts: ts, update_ts: ts, payload: message })
      })
      client.subscribe(bsmTopic, function (mes: IMessage) {
        const message: BsmFeature = JSON.parse(mes.body)
        const ts = Date.now()
        bsmStream.next({ type: 'bsm', rcv_ts: ts, update_ts: ts, payload: message })
      })
    },
    (error) => {
      console.error('Live Streaming ERROR connecting to live data Websocket: ' + error)
    }
  )

  client.onDisconnect = (frame) => {
    console.debug('Live Streaming Disconnected from STOMP endpoint: ' + frame + ' (numRestarts: ' + numRestarts + ')')
  }

  client.onStompError = (frame) => {
    console.error('Live Streaming STOMP ERROR', frame)
  }

  client.onWebSocketClose = (frame) => {
    console.debug('Live Streaming STOMP WebSocket Closed', frame)
  }

  client.onWebSocketError = (frame) => {
    // TODO: Consider restarting connection on error
    console.error('Live Streaming STOMP WebSocket Error', frame)
  }

  return { client, mapStream, spatStream, bsmStream }
}

class LiveIntersectionApi {
  // A map of subjects to simulate streams of data for different intersections
  dataStream = new Subject<liveIntersectionData>()
  batchedDataStream = new Subject<{
    maps: { [key: number]: ProcessedMap }
    spats: { [key: number]: ProcessedSpat }
    bsms: { [key: number]: { [key: string]: BsmFeature } }
  }>()
  intervalId = undefined as undefined | NodeJS.Timeout
  activeIntersections: number[] = []

  activeClients: {
    maps: { [key: number]: { stream: Subject<liveMap>; client: MinimalClient; subscription: Subscription } }
    spats: { [key: number]: { stream: Subject<liveSpat>; client: MinimalClient; subscription: Subscription } }
    bsms: { [key: number]: { stream: Subject<liveBsm>; client: MinimalClient; subscription: Subscription } }
  } = { maps: {}, spats: {}, bsms: {} }

  activeData: {
    maps: { [key: number]: ProcessedMap }
    spats: { [key: number]: ProcessedSpat }
    bsms: { [key: number]: { [key: string]: BsmFeature } }
  } = { maps: {}, spats: {}, bsms: {} }

  initialize = (
    callback: (data: {
      maps: { [key: number]: ProcessedMap }
      spats: { [key: number]: ProcessedSpat }
      bsms: { [key: number]: { [key: string]: BsmFeature } }
    }) => void
  ) => {
    if (this.intervalId) {
      clearInterval(this.intervalId)
    }
    this.intervalId = setInterval(() => {
      const clone = cloneDeep(this.activeData)
      this.batchedDataStream.next(clone)
      callback(clone)
    }, 1000)
  }

  viewBoundsChanged = (viewBounds: mapboxgl.LngLatBounds, allIntersections: IntersectionReferenceData[]) => {
    const intersections = allIntersections
      .filter((intersection) => {
        return viewBounds.contains(new mapboxgl.LngLat(intersection.longitude, intersection.latitude))
      })
      .map((intersection) => intersection.intersectionID)
    if (this.activeIntersections != intersections) {
      this.updateSubscriptionList(intersections)
    }
  }

  updateSubscriptionList = (intersections: number[]) => {
    this.activeIntersections = intersections

    // Remove old subscriptions
    Object.entries(this.activeClients).forEach(([type, clients]) => {
      Object.entries(clients).forEach(([key, { client }]) => {
        const intersectionId = parseInt(key, 10)
        if (!intersections.includes(intersectionId)) {
          client.disconnect()
          delete clients[intersectionId]
          delete this.activeClients[type][intersectionId]
          this.activeData[type][intersectionId] = undefined
        }
      })
    })

    // Add new subscriptions
    intersections
      .filter((intersection) => !Object.keys(this.activeClients.maps).includes(intersection.toString()))
      .forEach((intersectionId) => {
        // const { client, mapStream, spatStream, bsmStream } = createWebsocketConnection(
        //   'Bearer ' + 'token',
        //   'ws://' + intersection.rsuIP + ':8080/ws',
        //   intersection.intersectionID,
        //   intersection.roadRegulatorID
        // )
        // this.activeClients.maps[intersectionId] = { stream: mapStream, client }
        // this.activeClients.spats[intersectionId] = { stream: spatStream, client }
        // this.activeClients.bsms[intersectionId] = { stream: bsmStream, client }

        const mapStream = new Subject<liveMap>()
        const spatStream = new Subject<liveSpat>()
        const bsmStream = new Subject<liveBsm>()

        const mapClient = FakeLiveDataApi.startMockedMapData(intersectionId, mapStream)
        const mapSubscription = mapStream.subscribe((data) => {
          this.dataStream.next(data)
          this.activeData.maps[intersectionId] = data.payload
        })
        const spatClient = FakeLiveDataApi.startMockedSpatData(intersectionId, spatStream)
        const spatSubscription = spatStream.subscribe((data) => {
          this.dataStream.next(data)
          this.activeData.spats[intersectionId] = data.payload
        })
        const bsmClient = FakeLiveDataApi.startMockedBsmData(intersectionId, bsmStream)
        const bsmSubscription = bsmStream.subscribe((data) => {
          this.dataStream.next(data)
          if (!this.activeData.bsms[intersectionId]) {
            this.activeData.bsms[intersectionId] = {}
          }
          this.activeData.bsms[intersectionId][data.payload.properties.id] = data.payload
        })
        this.activeClients.maps[intersectionId] = {
          stream: mapStream,
          client: mapClient,
          subscription: mapSubscription,
        }
        this.activeClients.spats[intersectionId] = {
          stream: spatStream,
          client: spatClient,
          subscription: spatSubscription,
        }
        this.activeClients.bsms[intersectionId] = {
          stream: bsmStream,
          client: bsmClient,
          subscription: bsmSubscription,
        }
      })
  }
}

export default new LiveIntersectionApi()
