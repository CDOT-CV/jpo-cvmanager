import { createAsyncThunk, createSlice, PayloadAction } from '@reduxjs/toolkit'
import { RootState } from '../store'
import MessageMonitorApi from '../apis/intersections/mm-api'
import { selectToken } from './userSlice'

export const initialState = {
  intersections: [
    {
      intersectionID: -1,
      rsuIP: '0.0.0.0',
      latitude: 0,
      longitude: 0,
    },
  ] as IntersectionReferenceData[],
  selectedIntersection: null as IntersectionReferenceData | null,
  selectedIntersectionId: -1,
}

export const getIntersections = createAsyncThunk(
  'intersection/getIntersections',
  async (organizationName: string | undefined, { getState }) => {
    const currentState = getState() as RootState
    const authToken = selectToken(currentState)!

    const intersections = await MessageMonitorApi.getIntersections({ token: authToken, organization: organizationName })
    intersections.push({
      intersectionID: -1,
      rsuIP: '0.0.0.0',
      latitude: 0,
      longitude: 0,
    })
    return intersections
  },
  {
    condition: (_, { getState }) => selectToken(getState() as RootState) != undefined,
  }
)

export const intersectionSlice = createSlice({
  name: 'intersection',
  initialState: {
    loading: false,
    currentRequestId: null as string | null,
    value: initialState,
  },
  reducers: {
    setSelectedIntersection: (state, action: PayloadAction<number>) => {
      const intersection = state.value.intersections.find((i) => i.intersectionID === action.payload)
      if (intersection) {
        state.value.selectedIntersection = intersection
        state.value.selectedIntersectionId = action.payload
      } else {
        console.error(
          'Unable to select intersection. Intersection ' + action.payload + ' not found in list:',
          state.value.intersections
        )
      }
    },
    setSelectedIntersectionId: (state, action: PayloadAction<number>) => {
      state.value.selectedIntersectionId = action.payload
    },
    setIntersectionManual: (state, action: PayloadAction<IntersectionReferenceData>) => {
      state.value.intersections = [action.payload]
      state.value.selectedIntersection = action.payload
      state.value.selectedIntersectionId = action.payload[0].intersectionID
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(getIntersections.pending, (state, action) => {
        state.loading = true
        state.currentRequestId = action.meta.requestId
      })
      .addCase(getIntersections.fulfilled, (state, action) => {
        if (state.currentRequestId !== action.meta.requestId) return
        state.value.intersections = action.payload
        state.loading = false
        state.currentRequestId = null
      })
      .addCase(getIntersections.rejected, (state, action) => {
        if (state.currentRequestId !== action.meta.requestId) return
        state.loading = false
        state.currentRequestId = null
      })
  },
})

export const selectIntersections = (state: RootState) => state.intersection.value.intersections
export const selectSelectedIntersection = (state: RootState) => state.intersection.value.selectedIntersection
export const selectSelectedIntersectionId = (state: RootState) => state.intersection.value.selectedIntersectionId

export const { setSelectedIntersection, setSelectedIntersectionId } = intersectionSlice.actions

export default intersectionSlice.reducer
