import { IMessage, Stomp } from '@stomp/stompjs'
import { Subject, Subscription } from 'rxjs'
import FakeLiveDataApi from './fake-data-api'
import mapboxgl from 'mapbox-gl'
import EnvironmentVars from '../../../EnvironmentVars'

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
      //   const clone = cloneDeep(this.activeData)
      this.batchedDataStream.next(this.activeData)
      callback(this.activeData)
    }, 1000)
  }

  cancelAll = () => {
    this.updateSubscriptionList([], '')
  }

  viewBoundsChanged = (
    viewBounds: mapboxgl.LngLatBounds,
    allIntersections: IntersectionReferenceData[],
    isLayerActive: boolean,
    token: string
  ) => {
    const intersections = allIntersections
      .filter((intersection) => {
        return viewBounds.contains(new mapboxgl.LngLat(intersection.longitude, intersection.latitude))
      })
      .filter((_) => isLayerActive)
      .map((intersection) => intersection.intersectionID)
    if (this.activeIntersections != intersections) {
      this.updateSubscriptionList(intersections, token)
    }
  }

  updateSubscriptionList = (intersections: number[], token: string) => {
    console.log('live-intersection-api Updating subscription list', intersections, 'Active', this.activeIntersections)
    this.activeIntersections = intersections

    // Remove old subscriptions
    Object.entries(this.activeClients).forEach(([type, clients]) => {
      Object.entries(clients).forEach(([key, { client, subscription }]) => {
        const intersectionId = parseInt(key, 10)
        if (!intersections.includes(intersectionId) && client != null) {
          console.log('live-intersection-api Unsubscribing from intersection ' + intersectionId)
          client.disconnect()
          subscription.unsubscribe()
          delete clients[intersectionId]
          delete this.activeClients[type][intersectionId]
          this.activeData = { ...this.activeData, [type]: { ...this.activeData[type], [intersectionId]: undefined } }
        }
      })
    })

    const intersectionIds = intersections.filter(
      (intersection) => !Object.keys(this.activeClients.maps).includes(intersection.toString())
    )

    intersectionIds.forEach((intersectionId) => {
      this.activeClients.maps[intersectionId] = {
        stream: null,
        client: null,
        subscription: null,
      }
      this.activeClients.spats[intersectionId] = {
        stream: null,
        client: null,
        subscription: null,
      }
      this.activeClients.bsms[intersectionId] = {
        stream: null,
        client: null,
        subscription: null,
      }
    })

    // Add new subscriptions
    intersectionIds.forEach((intersectionId) => {
      console.log('live-intersection-api Subscribing to intersection ' + intersectionId)
      const { client, mapStream, spatStream, bsmStream } = this.createWebsocketConnection(
        token,
        `${EnvironmentVars.CVIZ_API_WS_URL}/stomp`,
        intersectionId,
        -1
      )
      if (client == null) {
        delete this.activeClients.maps[intersectionId]
        delete this.activeClients.spats[intersectionId]
        delete this.activeClients.bsms[intersectionId]
        return
      }

      //   const mapStream = new Subject<liveMap>()
      //   const spatStream = new Subject<liveSpat>()
      //   const bsmStream = new Subject<liveBsm>()

      //   const mapClient = FakeLiveDataApi.startMockedMapData(intersectionId, mapStream)
      const mapSubscription = mapStream.subscribe((data) => {
        this.dataStream.next(data)
        this.activeData.maps = { ...this.activeData.maps, [intersectionId]: data.payload }
      })
      //   const spatClient = FakeLiveDataApi.startMockedSpatData(intersectionId, spatStream)
      const spatSubscription = spatStream.subscribe((data) => {
        this.dataStream.next(data)
        this.activeData.spats = { ...this.activeData.spats, [intersectionId]: data.payload }
      })
      //   const bsmClient = FakeLiveDataApi.startMockedBsmData(intersectionId, bsmStream)
      const bsmSubscription = bsmStream.subscribe((data) => {
        this.dataStream.next(data)
        this.activeData.bsms = {
          ...this.activeData.bsms,
          [intersectionId]: {
            ...(this.activeData.bsms[intersectionId] ?? {}),
            [data.payload.properties.id]: data.payload,
          },
        }
      })
      this.activeClients.maps[intersectionId] = {
        stream: mapStream,
        client,
        // client: mapClient,
        subscription: mapSubscription,
      }
      this.activeClients.spats[intersectionId] = {
        stream: spatStream,
        client,
        // client: spatClient,
        subscription: spatSubscription,
      }
      this.activeClients.bsms[intersectionId] = {
        stream: bsmStream,
        client,
        // client: bsmClient,
        subscription: bsmSubscription,
      }
    })
  }

  createWebsocketConnection = (
    token: string,
    url: string,
    intersectionId: number,
    roadRegulatorId: number,
    numRestarts: number = 0,
    inputMapStream: Subject<liveMap> = null,
    inputSpatStream: Subject<liveSpat> = null,
    inputBsmStream: Subject<liveBsm> = null
  ): {
    client: MinimalClient
    mapStream: Subject<liveMap>
    spatStream: Subject<liveSpat>
    bsmStream: Subject<liveBsm>
  } => {
    if (!this.activeIntersections.includes(intersectionId)) {
      console.debug('Not connecting to intersection ' + intersectionId + ' as it is not active')
      return { client: null, mapStream: null, spatStream: null, bsmStream: null }
    }
    let protocols = ['v10.stomp', 'v11.stomp']
    protocols.push(token)
    console.debug('Connecting to live STOMP endpoint: ' + url + ' with intersectionId: ' + intersectionId)

    // Stomp Client Documentation: https://stomp-js.github.io/stomp-websocket/codo/extra/docs-src/Usage.md.html
    let client = Stomp.client(url, protocols)
    client.debug = (e) => {
      console.debug('STOMP Debug: ' + e)
    }

    // Topics are in the format /live/{roadRegulatorID}/{intersectionID}/{spat,map,bsm}
    let spatTopic = `/live/${roadRegulatorId}/${intersectionId}/spat`
    let mapTopic = `/live/${roadRegulatorId}/${intersectionId}/map`
    let bsmTopic = `/live/${roadRegulatorId}/${intersectionId}/bsm` // TODO: Filter by road regulator ID

    const mapStream = inputMapStream ?? new Subject<liveMap>()
    const spatStream = inputSpatStream ?? new Subject<liveSpat>()
    const bsmStream = inputBsmStream ?? new Subject<liveBsm>()
    let connectionStartTime = Date.now()

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
        console.error(
          'Live Intersection Streaming ERROR connecting to live data Websocket:  for intersection ' +
            intersectionId +
            ', ' +
            error
        )
      }
    )

    client.onStompError = (frame) => {
      console.error('Live Intersection Streaming STOMP ERROR for intersection ' + intersectionId, frame)
    }

    client.onWebSocketClose = (frame) => {
      console.debug('Live Intersection Streaming STOMP WebSocket Closed for intersection ' + intersectionId, frame)
      if (numRestarts < 5) {
        let numRestartsLocal = numRestarts
        if (Date.now() - connectionStartTime > 10000) {
          numRestartsLocal = 0
        }
        console.debug(
          'Attempting to reconnect to live intersection STOMP endpoint (numRestarts: ' +
            numRestartsLocal +
            ') for intersection ' +
            intersectionId
        )

        this.createWebsocketConnection(
          token,
          url,
          intersectionId,
          roadRegulatorId,
          numRestartsLocal + 1,
          mapStream,
          spatStream,
          bsmStream
        )
      }
    }

    client.onWebSocketError = (frame) => {
      // TODO: Consider restarting connection on error
      console.error('Live Intersection Streaming STOMP WebSocket Error for intersection ' + intersectionId, frame)
    }

    return { client, mapStream, spatStream, bsmStream }
  }
}

export default new LiveIntersectionApi()
