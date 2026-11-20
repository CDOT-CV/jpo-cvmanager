import { fireEvent, render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { vi } from 'vitest'
import { testTheme } from '../../styles'
import FirmwareTabs from './FirmwareTabs'

vi.mock('./AdminFirmwareTab', () => ({ default: () => <div>RSU firmware table</div> }))
vi.mock('../adminObuFirmwareTab/AdminObuFirmwareTab', () => ({ default: () => <div>OBU firmware table</div> }))

const CurrentPath = () => <div data-testid="path">{useLocation().pathname}</div>

// Mounted the way VerticalTabs mounts each admin tab, at <path>/*
const renderAt = (path: string) =>
  render(
    <ThemeProvider theme={testTheme}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/dashboard/admin/firmware/*" element={<FirmwareTabs />} />
        </Routes>
        <CurrentPath />
      </MemoryRouter>
    </ThemeProvider>
  )

describe('Firmware tabs', () => {
  it('opens RSU firmware by default', () => {
    renderAt('/dashboard/admin/firmware')

    expect(screen.getByText('RSU firmware table')).toBeInTheDocument()
    expect(screen.queryByText('OBU firmware table')).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'RSU Firmware' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByTestId('path')).toHaveTextContent('/dashboard/admin/firmware/rsu')
  })

  it('opens OBU firmware from its URL', () => {
    renderAt('/dashboard/admin/firmware/obu')

    expect(screen.getByText('OBU firmware table')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'OBU Firmware' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'RSU Firmware' })).toHaveAttribute('aria-selected', 'false')
  })

  it('switches between firmware types and mounts only the selected table', () => {
    renderAt('/dashboard/admin/firmware/rsu')

    fireEvent.click(screen.getByRole('tab', { name: 'OBU Firmware' }))
    expect(screen.getByTestId('path')).toHaveTextContent('/dashboard/admin/firmware/obu')
    expect(screen.getByText('OBU firmware table')).toBeInTheDocument()
    expect(screen.queryByText('RSU firmware table')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: 'RSU Firmware' }))
    expect(screen.getByTestId('path')).toHaveTextContent('/dashboard/admin/firmware/rsu')
    expect(screen.getByText('RSU firmware table')).toBeInTheDocument()
    expect(screen.queryByText('OBU firmware table')).not.toBeInTheDocument()
  })

  it('redirects unknown firmware paths to RSU firmware', () => {
    renderAt('/dashboard/admin/firmware/other/page')

    expect(screen.getByTestId('path')).toHaveTextContent('/dashboard/admin/firmware/rsu')
    expect(screen.getByText('RSU firmware table')).toBeInTheDocument()
  })
})
