import ReactDOM from 'react-dom'
import { Provider } from 'react-redux'
import { ThemeProvider } from '@mui/material'
import { testTheme } from './styles'
import 'mapbox-gl/dist/mapbox-gl.css'
import { setupStore } from './store'
import App from './App'
import { setupListeners } from '@reduxjs/toolkit/query'

const store = setupStore({})
setupListeners(store.dispatch)

ReactDOM.render(
  <ThemeProvider theme={testTheme}>
    <Provider store={store}>
      <App />
    </Provider>
  </ThemeProvider>,
  document.getElementById('root')
)
