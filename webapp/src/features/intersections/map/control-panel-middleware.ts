import { createListenerMiddleware, isAnyOf } from '@reduxjs/toolkit'
import { setMode } from './dataSourceSlice'
import { addMessages } from './mapDataSlice'

const listenerMiddleware = createListenerMiddleware()

listenerMiddleware.startListening({
  matcher: isAnyOf(setMode),
  effect: async (action, listenerApi) => {
    const mode = action.payload

    if (mode === 'live') {
      const ws = new WebSocket('wss://example.com/messages')

      ws.onmessage = (event) => {
        const msg = JSON.parse(event.data)
        listenerApi.dispatch(addMessages([msg]))
      }

      // Store socket ref so we can close later
      listenerApi.extra.liveSocket = ws
    } else {
      // Stop live mode
      listenerApi.extra.liveSocket?.close()
      listenerApi.extra.liveSocket = null
    }
  },
})
