import { useEffect, useRef } from 'react'
// import { addBsmMultiple } from '../features/intersections/live/live-intersection-slice'
import { useAppDispatch } from '../hooks'

// Function to batch BSMs and dispatch in intervals
const useBsmBatching = () => {
  const dispatch = useAppDispatch()
  const bsmBuffer = useRef<BsmFeature[]>([]) // Buffer to store BSMs temporarily
  const batchInterval = 100 // 100ms interval

  const receiveBsm = (bsm: BsmFeature) => {
    bsmBuffer.current.push(bsm)
  }

  useEffect(() => {
    // Function to push incoming BSMs to the buffer

    // Set up the interval to dispatch the batched BSMs every 100ms
    const intervalId = setInterval(() => {
      if (bsmBuffer.current.length > 0) {
        // Dispatch the batch to Redux store or update the map
        // dispatch(addBsmMultiple(bsmBuffer.current))

        // Clear the buffer after dispatch
        bsmBuffer.current = []
      }
    }, batchInterval)

    // Cleanup the interval on unmount
    return () => {
      clearInterval(intervalId)
    }
  }, [dispatch])

  return { receiveBsm }
}

export default useBsmBatching
