import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import AdminTable from './AdminTable'
import { replaceChaoticIds } from '../utils/test-utils'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../styles'

it('should take a snapshot', () => {
  const { container } = render(
    <ThemeProvider theme={testTheme}>
      <AdminTable actions={[]} columns={[]} data={[]} title={''} />
    </ThemeProvider>
  )

  expect(replaceChaoticIds(container)).toMatchSnapshot()
})

it('shows an empty table as a single row rather than a page of blank rows', async () => {
  const handleQueryChange = vi.fn().mockResolvedValue({ data: [], page: 0, totalCount: 0 })

  render(
    <ThemeProvider theme={testTheme}>
      <AdminTable
        actions={[]}
        columns={[{ title: 'Name', field: 'name' }]}
        defaultPageSize={25}
        handleQueryChange={handleQueryChange}
        title=""
      />
    </ThemeProvider>
  )

  const message = await screen.findByText('No records to display')
  const emptyRow = message.closest('tr') as HTMLElement
  expect(emptyRow.style.height).not.toBe('')
  expect(parseFloat(emptyRow.style.height)).toBeLessThan(100)
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
