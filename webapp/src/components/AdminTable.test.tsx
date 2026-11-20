import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { vi } from 'vitest'
import AdminTable from './AdminTable'
import { replaceChaoticIds } from '../utils/test-utils'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'
import { useState } from 'react'

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <AdminTable actions={[]} columns={[]} data={[]} title={''} />
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('debounces server-side search and submits only the final value', async () => {
  const handleQueryChange = vi.fn().mockResolvedValue({ data: [], page: 0, totalCount: 0 })

  render(
    <ThemeProvider theme={testTheme}>
      <AdminTable actions={[]} columns={[]} handleQueryChange={handleQueryChange} title="" />
    </ThemeProvider>
  )
  await waitFor(() => expect(handleQueryChange).toHaveBeenCalled())
  handleQueryChange.mockClear()

  fireEvent.change(screen.getByPlaceholderText('Search'), { target: { value: 'firm' } })
  fireEvent.change(screen.getByPlaceholderText('Search'), { target: { value: 'firmware' } })

  await waitFor(() => expect(handleQueryChange).toHaveBeenCalledTimes(1))
  expect(handleQueryChange).toHaveBeenCalledWith(expect.objectContaining({ page: 0, search: 'firmware' }))
})

it('preserves selected rows across server-side pages', async () => {
  const rows = [{ ip: 'rsu-1' }, { ip: 'rsu-2' }]
  const handleQueryChange = vi.fn(async (query) => ({
    data: [rows[query.page]],
    page: query.page,
    totalCount: rows.length,
  }))

  const SelectionTable = () => {
    const [selectedRows, setSelectedRows] = useState<any[]>([])
    return (
      <>
        <output data-testid="selected-rows">{selectedRows.map((row) => row.ip).join(',')}</output>
        <AdminTable
          actions={[
            {
              icon: () => null,
              position: 'toolbarOnSelect',
              onClick: () => {},
            },
          ]}
          columns={[{ title: 'IP Address', field: 'ip' }]}
          title=""
          handleQueryChange={handleQueryChange}
          defaultPageSize={1}
          pageSizeOptions={[1]}
          selectionKeyField="ip"
          selectedRows={selectedRows}
          onSelectionChange={setSelectedRows}
        />
      </>
    )
  }

  render(
    <ThemeProvider theme={testTheme}>
      <SelectionTable />
    </ThemeProvider>
  )

  const selectVisibleRow = (ip: string) => {
    const row = screen.getByText(ip).closest('tr')
    if (!row) {
      throw new Error(`Could not find table row for ${ip}`)
    }
    fireEvent.click(within(row).getByRole('checkbox'))
  }

  await screen.findByText('rsu-1')
  selectVisibleRow('rsu-1')
  await waitFor(() => expect(screen.getByTestId('selected-rows').textContent).toBe('rsu-1'))
  expect(screen.getByRole('heading', { name: '1 row(s) selected' })).toBeInTheDocument()
  expect(screen.getByRole('button')).toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: 'Next Page' }))
  await screen.findByText('rsu-2')
  expect(screen.getByRole('heading', { name: '1 row(s) selected' })).toBeInTheDocument()
  expect(screen.getByRole('button')).toBeInTheDocument()
  selectVisibleRow('rsu-2')
  await waitFor(() => expect(screen.getByTestId('selected-rows').textContent).toBe('rsu-1,rsu-2'))
  expect(screen.getByRole('heading', { name: '2 row(s) selected' })).toBeInTheDocument()
  expect(screen.getByRole('button')).toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: 'Previous Page' }))
  await screen.findByText('rsu-1')
  const row = screen.getByText('rsu-1').closest('tr')
  expect(row && within(row).getByRole('checkbox')).toBeChecked()
})
