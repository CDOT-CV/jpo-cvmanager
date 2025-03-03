import { createSlice } from '@reduxjs/toolkit'
import { RootState } from '../../../../store'

const initialState = {}

export const intersectionMapDataSlice = createSlice({
  name: 'intersectionMapData',
  initialState: {
    loading: false,
    value: initialState,
  },
  reducers: {},
  extraReducers: (builder) => {
    builder
  },
})

export const selectLoading = (state: RootState) => state.intersectionMapData.loading

export const {} = intersectionMapDataSlice.actions

export default intersectionMapDataSlice.reducer
