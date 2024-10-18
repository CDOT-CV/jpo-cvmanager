import { IMessage, Stomp } from '@stomp/stompjs'
import { merge, Observable, Subject, Subscription } from 'rxjs'
import FakeLiveDataApi from './fake-data-api'
import mapboxgl from 'mapbox-gl'

export interface MinimalClient {
  connect: (headers: unknown, connectCallback: () => void, errorCallback?: (error: string) => void) => void
  subscribe: (destination: string, callback: (message: IMessage) => void) => void
  disconnect: (disconnectCallback?: () => void) => void
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
      console.log('Updating batched data', this.activeData)
      this.batchedDataStream.next(this.activeData)
      callback(this.activeData)
    }, 1000)
  }

  viewBoundsChanged = (viewBounds: mapboxgl.LngLatBounds, allIntersections: IntersectionReferenceData[]) => {
    const intersections = allIntersections
      .filter((intersection) => {
        return viewBounds.contains(new mapboxgl.LngLat(intersection.longitude, intersection.latitude))
      })
      .map((intersection) => intersection.intersectionID)
    console.log('Updating Intersection Subscriptions', intersections)
    if (this.activeIntersections != intersections) {
      this.updateSubscriptionList(intersections)
    }
  }

  updateSubscriptionList = (intersections: number[]) => {
    console.log('Updating Intersection Subscription List', intersections, this.activeClients)
    this.activeIntersections = intersections

    // Remove old subscriptions
    Object.entries(this.activeClients).forEach(([type, clients]) => {
      Object.entries(clients).forEach(([key, { client }]) => {
        const intersectionId = parseInt(key, 10)
        if (!intersections.includes(intersectionId)) {
          console.log('removing subscriptions for intersection', intersectionId)
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
        console.log('adding subscriptions for intersection', intersectionId)

        const mapStream = new Subject<liveMap>()
        const spatStream = new Subject<liveSpat>()
        const bsmStream = new Subject<liveBsm>()

        const mapClient = FakeLiveDataApi.startMockedMapData(intersectionId, mapStream)
        const mapSubscription = mapStream.subscribe((data) => {
          console.log('mapSubscription', data)
          this.dataStream.next(data)
          this.activeData.maps[intersectionId] = data.payload
        })
        // const spatClient = FakeLiveDataApi.startMockedSpatData(intersectionId, spatStream)
        // const spatSubscription = spatStream.subscribe((data) => {
        //   console.log('spatSubscription', data)
        //   this.dataStream.next(data)
        //   this.activeData.spats[intersectionId] = data.payload
        // })
        const bsmClient = FakeLiveDataApi.startMockedBsmData(intersectionId, bsmStream)
        const bsmSubscription = bsmStream.subscribe((data) => {
          // console.log('bsmSubscription', data)
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
        // this.activeClients.spats[intersectionId] = {
        //   stream: spatStream,
        //   client: spatClient,
        //   subscription: spatSubscription,
        // }
        this.activeClients.bsms[intersectionId] = {
          stream: bsmStream,
          client: bsmClient,
          subscription: bsmSubscription,
        }
      })
  }
}

export default new LiveIntersectionApi()
