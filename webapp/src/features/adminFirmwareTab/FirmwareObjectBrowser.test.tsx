import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import AdminFirmwareTab from './AdminFirmwareTab'
import toast from 'react-hot-toast'
import { ThemeProvider } from '@mui/material'
import { testTheme } from '../../styles'
import {
  useDeleteFirmwareObjectMutation,
  useGetFirmwareUploadOptionsQuery,
  useLazyListFirmwareObjectsQuery,
  useListFirmwareRulesQuery,
} from '../api/firmwareApiSlice'

vi.mock('../api/firmwareApiSlice', () => ({
  useGetFirmwareUploadOptionsQuery: vi.fn(),
  useLazyListFirmwareObjectsQuery: vi.fn(),
  useDeleteFirmwareObjectMutation: vi.fn(),
  useListFirmwareRulesQuery: vi.fn(),
}))
vi.mock('./FirmwareRulesDialog', () => ({ default: () => <div>Upgrade path editor</div> }))

// Keep feature tests focused on firmware queries and actions. AdminTable owns
// Material Table rendering and debounce behavior, which are tested separately.
vi.mock('../../components/AdminTable', async () => {
  const React = await vi.importActual<typeof import('react')>('react')

  return {
    default: ({ actions, columns, defaultPageSize = 25, handleQueryChange, tableRef, thirdSortClick = true }: any) => {
      const [rows, setRows] = React.useState<any[]>([])
      const [totalCount, setTotalCount] = React.useState(0)
      const queryRef = React.useRef({ page: 0, pageSize: defaultPageSize, search: '' })

      const runQuery = React.useCallback(async (changes: Record<string, unknown> = {}) => {
        const query = { ...queryRef.current, ...changes }
        queryRef.current = query
        const result = await handleQueryChange(query)
        setRows(result.data)
        setTotalCount(result.totalCount)
      }, [handleQueryChange])

      React.useEffect(() => {
        tableRef.current = { onQueryChange: runQuery }
        void runQuery()
      }, [runQuery, tableRef])

      const toolbarActions = actions.filter((action: any) => typeof action !== 'function' && action.position === 'toolbar')

      return (
        <div>
          <input
            placeholder="Search"
            value={queryRef.current.search}
            onChange={(event) => void runQuery({ page: 0, search: event.target.value })}
          />
          {toolbarActions.map((action: any, index: number) =>
            action.iconProps?.itemType === 'custom' ? (
              <React.Fragment key={index}>{action.iconProps.render()}</React.Fragment>
            ) : (
              <button key={index} onClick={action.onClick} disabled={action.disabled}>
                {action.iconProps?.title}
              </button>
            )
          )}
          <table>
            <thead>
              <tr>
                {columns.map((column: any, index: number) => (
                  <th
                    key={column.field ?? column.title}
                    style={column.headerStyle}
                    onClick={() => {
                      const currentSort = (queryRef.current as any).orderByCollection?.[0]
                      let orderDirection = 'asc'
                      if (currentSort?.orderBy === index) {
                        orderDirection = currentSort.orderDirection === 'asc'
                          ? 'desc'
                          : thirdSortClick ? '' : 'asc'
                      }
                      void runQuery({
                        page: 0,
                        orderByCollection: orderDirection ? [{ orderBy: index, orderDirection }] : [],
                      })
                    }}
                  >
                    {column.title}
                  </th>
                ))}
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row: any) => (
                <tr key={row.object_id}>
                  {columns.map((column: any) => (
                    <td key={column.field ?? column.title}>{column.render ? column.render(row) : row[column.field]}</td>
                  ))}
                  <td>
                    {actions.filter((action: any) => typeof action === 'function').map((action: any, index: number) => {
                      const rowAction = action(row)
                      return (
                        <button
                          key={index}
                          aria-label={rowAction.tooltip}
                          disabled={rowAction.disabled}
                          onClick={(event) => rowAction.onClick(event, row)}
                        >
                          {rowAction.tooltip}
                        </button>
                      )
                    })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <button
            aria-label="Next Page"
            disabled={(queryRef.current.page + 1) * queryRef.current.pageSize >= totalCount}
            onClick={() => void runQuery({ page: queryRef.current.page + 1 })}
          >
            Next
          </button>
        </div>
      )
    },
  }
})
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn(), loading: vi.fn() } }))
vi.mock('./FirmwareUploadForm', () => ({
  default: ({ open, onSuccess }: { open: boolean; onSuccess: (firmwareId?: number) => void }) =>
    open ? (
      <div>
        Upload form<button onClick={() => onSuccess(12)}>Complete mocked upload</button>
      </div>
    ) : null,
}))

describe('Firmware object browser', () => {
  const renderFirmware = () =>
    render(
      <ThemeProvider theme={testTheme}>
        <AdminFirmwareTab />
      </ThemeProvider>
    )
  const trigger = vi.fn()
  const lazyQuery = vi.mocked(useLazyListFirmwareObjectsQuery)
  const optionsQuery = vi.mocked(useGetFirmwareUploadOptionsQuery)
  const deleteObject = vi.fn()

  const page = {
    objects: [
      {
        object_id: 'one',
        firmware_id: 12,
        object_name: 'Commsignia/ITS-RS4-M/v1/file.bin',
        manufacturer: 'Commsignia',
        model: 'ITS-RS4-M',
        version: 'v1',
        file_name: 'file.bin',
        content_length: 9,
        verification_status: 'VERIFIED' as const,
        upload_status: 'VERIFIED',
        provider_object_version: '17',
      },
      {
        object_id: 'two',
        object_name: 'Kapsch/RIS-9260/v2/update.tar',
        manufacturer: 'Kapsch',
        model: 'RIS-9260',
        version: 'v2',
        file_name: 'update.tar',
        content_length: 3,
        verification_status: 'UNTRACKED' as const,
        provider_object_version: '18',
      },
    ],
    total_elements: 2,
  }

  beforeEach(() => {
    vi.resetAllMocks()
    trigger.mockImplementation(() => ({ unwrap: () => Promise.resolve(page) }))
    lazyQuery.mockReturnValue([trigger] as any)
    vi.mocked(useListFirmwareRulesQuery).mockReturnValue({ data: [], refetch: vi.fn() } as any)
    vi.mocked(useDeleteFirmwareObjectMutation).mockReturnValue([deleteObject] as any)
    deleteObject.mockImplementation(() => ({ unwrap: () => Promise.resolve() }))
    optionsQuery.mockReturnValue({
      data: {
        manufacturers: [
          { manufacturer_id: 1, name: 'Commsignia', file_extension: '.tar.sig', models: [] },
          { manufacturer_id: 2, name: 'Kapsch', file_extension: null, models: [] },
        ],
      },
      isFetching: false,
    } as any)
  })

  it('provides firmware columns and controls through the admin table', async () => {
    renderFirmware()

    expect(await screen.findByText('v1')).toBeInTheDocument()
    expect(screen.getByText('v2')).toBeInTheDocument()
    expect(screen.getAllByRole('columnheader', { name: 'Manufacturer' })[0]).toHaveStyle({ textTransform: 'none' })
    expect(screen.getAllByRole('columnheader', { name: 'Model' })[0]).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Search')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Manufacturer' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New' })).toBeInTheDocument()

    fireEvent.click(screen.getByText('v1'))
    expect(screen.queryByRole('button', { name: 'v1' })).not.toBeInTheDocument()
    expect(screen.queryByText('Upgrade path editor')).not.toBeInTheDocument()
    expect(screen.queryByText('File details')).not.toBeInTheDocument()
  })

  it('makes verified firmware without rules visible and opens the rule editor', async () => {
    renderFirmware()
    expect(screen.getByRole('columnheader', { name: 'Upgrade Rules' })).toBeInTheDocument()
    const paths = await screen.findByRole('button', { name: 'No upgrade rules' })
    fireEvent.click(paths)
    expect(screen.getByText('Upgrade path editor')).toBeInTheDocument()
  })

  it('counts incoming and outgoing rules together without labeling the firmware as legacy', async () => {
    const image = (firmware_id: number) => ({ firmware_id })
    vi.mocked(useListFirmwareRulesQuery).mockReturnValue({
      data: [
        { rule_id: 1, source: image(10), destination: image(12), legacy_destination: true },
        { rule_id: 2, source: image(12), destination: image(13), legacy_destination: false },
        { rule_id: 3, source: image(14), destination: image(15), legacy_destination: false },
      ],
      refetch: vi.fn(),
    } as any)
    renderFirmware()
    fireEvent.click(await screen.findByRole('button', { name: '2 rules' }))
    expect(screen.queryByText(/Legacy rules/)).not.toBeInTheDocument()
    expect(screen.getByText('Upgrade path editor')).toBeInTheDocument()
  })

  it('returns to the table after upload without opening upgrade paths', async () => {
    renderFirmware()
    await screen.findByText('v1')
    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    fireEvent.click(screen.getByRole('button', { name: 'Complete mocked upload' }))
    expect(screen.queryByText('Upgrade path editor')).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Refresh' })).not.toBeDisabled())
    expect(screen.queryByText('Upload form')).not.toBeInTheDocument()
  })

  it('reloads the first page when a manufacturer is selected', async () => {
    renderFirmware()
    await screen.findByText('v1')

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
    fireEvent.click(screen.getByRole('option', { name: 'Kapsch' }))

    await waitFor(() =>
      expect(trigger).toHaveBeenLastCalledWith({
        manufacturer: 'Kapsch',
        page: 0,
        size: 25,
        search: '',
        sort: 'manufacturer,asc',
      })
    )
  })

  it('uses numbered pages and the server result count for pagination', async () => {
    trigger.mockImplementation(() => ({
      unwrap: () => Promise.resolve({ ...page, total_elements: 26 }),
    }))
    renderFirmware()
    await screen.findByText('v1')

    fireEvent.click(screen.getByRole('button', { name: 'Next Page' }))

    await waitFor(() =>
      expect(trigger).toHaveBeenLastCalledWith({
        manufacturer: undefined,
        page: 1,
        size: 25,
        search: '',
        sort: 'manufacturer,asc',
      })
    )
  })

  it('alternates server-side sorting without clearing the selected column', async () => {
    renderFirmware()
    await screen.findByText('v1')
    trigger.mockClear()

    const versionHeader = screen.getByRole('columnheader', { name: 'Version' })
    fireEvent.click(versionHeader)

    await waitFor(() => expect(trigger).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(trigger).toHaveBeenLastCalledWith({
      manufacturer: undefined,
      page: 0,
      size: 25,
      search: '',
      sort: 'version,asc',
    }))

    fireEvent.click(versionHeader)
    await waitFor(() => expect(trigger).toHaveBeenCalledTimes(2))
    expect(trigger).toHaveBeenLastCalledWith({
      manufacturer: undefined,
      page: 0,
      search: '',
      size: 25,
      sort: 'version,desc',
    })

    fireEvent.click(versionHeader)
    await waitFor(() => expect(trigger).toHaveBeenCalledTimes(3))
    expect(trigger).toHaveBeenLastCalledWith({
      manufacturer: undefined,
      page: 0,
      search: '',
      size: 25,
      sort: 'version,asc',
    })
  })

  describe('server-side search', () => {
    beforeEach(async () => {
      trigger.mockImplementation(({ search }) => ({
        unwrap: () =>
          Promise.resolve(
            search
              ? {
                  objects: [{ ...page.objects[0], object_id: 'later', version: 'later-release' }],
                  total_elements: 1,
                }
              : { ...page, total_elements: 26 }
          ),
      }))
      renderFirmware()
      await screen.findByText('v1')
    })

    afterEach(() => {
      cleanup()
    })

    const searchFor = async (value: string) => {
      const previousCallCount = trigger.mock.calls.length
      fireEvent.change(screen.getByPlaceholderText('Search'), { target: { value } })
      await waitFor(
        () => {
          expect(trigger.mock.calls.length).toBeGreaterThan(previousCallCount)
          expect(trigger).toHaveBeenLastCalledWith(expect.objectContaining({ search: value }))
        },
        { timeout: 2000 }
      )
    }

    it('searches from page zero and displays matches not previously loaded', async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Next Page' }))
      await waitFor(() =>
        expect(trigger).toHaveBeenLastCalledWith({
          page: 1,
          size: 25,
          search: '',
          manufacturer: undefined,
          sort: 'manufacturer,asc',
        })
      )

      await searchFor('later-release')

      expect(trigger).toHaveBeenLastCalledWith({
        page: 0,
        size: 25,
        search: 'later-release',
        manufacturer: undefined,
        sort: 'manufacturer,asc',
      })
      expect(screen.getByText('later-release')).toBeInTheDocument()
      expect(screen.queryByText('v1')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Next Page' })).toBeDisabled()
    })

    it('preserves the search when filtering by manufacturer and refreshing', async () => {
      await searchFor('later-release')
      fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
      fireEvent.click(screen.getByRole('option', { name: 'Kapsch' }))
      await waitFor(() =>
        expect(trigger).toHaveBeenLastCalledWith({
          page: 0,
          size: 25,
          search: 'later-release',
          manufacturer: 'Kapsch',
          sort: 'manufacturer,asc',
        })
      )

      trigger.mockClear()
      fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))

      await waitFor(() => expect(trigger).toHaveBeenCalledTimes(1))
      expect(trigger).toHaveBeenLastCalledWith({
        page: 0,
        size: 25,
        search: 'later-release',
        manufacturer: 'Kapsch',
        sort: 'manufacturer,asc',
      })
      expect(screen.getByText('later-release')).toBeInTheDocument()
    })

    it('restores the listing when search is cleared without losing the manufacturer filter', async () => {
      fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
      fireEvent.click(screen.getByRole('option', { name: 'Kapsch' }))
      await searchFor('later-release')
      expect(screen.queryByText('v1')).not.toBeInTheDocument()

      await searchFor('')

      expect(trigger).toHaveBeenLastCalledWith({
        page: 0,
        size: 25,
        search: '',
        manufacturer: 'Kapsch',
        sort: 'manufacturer,asc',
      })
      expect(screen.getByText('v1')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Next Page' })).toBeEnabled()
    })

    it('deletes a search result and refreshes with the current search and manufacturer', async () => {
      fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Manufacturer' }))
      fireEvent.click(screen.getByRole('option', { name: 'Commsignia' }))
      await searchFor('later-release')


      fireEvent.click(screen.getByRole('button', { name: 'Delete Firmware' }))
      expect(deleteObject).not.toHaveBeenCalled()
      trigger.mockClear()
      trigger.mockImplementation(() => ({ unwrap: () => Promise.resolve({ objects: [], total_elements: 0 }) }))
      fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

      await waitFor(() =>
        expect(deleteObject).toHaveBeenCalledWith({ object_id: 'later', provider_object_version: '17' })
      )
      await waitFor(() => expect(trigger).toHaveBeenCalledTimes(1))
      expect(trigger).toHaveBeenLastCalledWith({
        page: 0,
        size: 25,
        search: 'later-release',
        manufacturer: 'Commsignia',
        sort: 'manufacturer,asc',
      })
      await waitFor(() => expect(screen.queryByText('later-release')).not.toBeInTheDocument())
      expect(screen.queryByText('File details')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Next Page' })).toBeDisabled()
      expect(toast.success).toHaveBeenCalledWith('Firmware deleted successfully', expect.any(Object))
    })
  })

  it('refreshes the table after a successful upload', async () => {
    renderFirmware()
    await screen.findByText('v1')

    fireEvent.click(screen.getByRole('button', { name: 'New' }))
    expect(screen.getByText('Upload form')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Complete mocked upload' }))
    expect(screen.queryByText('Upload form')).not.toBeInTheDocument()
    await waitFor(() => expect(trigger).toHaveBeenCalledTimes(2))
  })

  it('confirms deletion and refreshes the table after the API succeeds', async () => {
    renderFirmware()
    await screen.findByText('v1')
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete Firmware' })[0])

    const details = screen.getByText(/Manufacturer: Commsignia/)
    expect(details.textContent).toBe('Manufacturer: Commsignia\nModel: ITS-RS4-M\nVersion: v1\nFile: file.bin')
    expect(details).toHaveStyle({ whiteSpace: 'pre-line', overflowWrap: 'anywhere' })
    expect(deleteObject).not.toHaveBeenCalled()
    trigger.mockImplementation(() => ({ unwrap: () => Promise.resolve({ ...page, objects: [page.objects[1]] }) }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() => expect(deleteObject).toHaveBeenCalledWith({ object_id: 'one', provider_object_version: '17' }))
    await waitFor(() => expect(screen.queryByText('v1')).not.toBeInTheDocument())
    expect(screen.getByText('v2')).toBeInTheDocument()
    expect(toast.success).toHaveBeenCalledWith('Firmware deleted successfully', expect.any(Object))
  })

  it('cancels deletion without changing the listing', async () => {
    renderFirmware()
    await screen.findByText('v1')
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete Firmware' })[1])
    expect(screen.getByText(/File: update.tar/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'No' }))
    expect(deleteObject).not.toHaveBeenCalled()
    expect(screen.getByText('v2')).toBeInTheDocument()
    expect(trigger).toHaveBeenCalledTimes(1)
  })

  it('keeps an untracked file listed and displays the API conflict message', async () => {
    deleteObject.mockImplementation(() => ({
      unwrap: () => Promise.reject({ status: 409, data: { detail: 'The firmware file changed. Refresh the table.' } }),
    }))
    renderFirmware()
    await screen.findByText('v2')
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete Firmware' })[1])
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('The firmware file changed. Refresh the table.', expect.any(Object))
    )
    expect(deleteObject).toHaveBeenCalledWith({ object_id: 'two', provider_object_version: '18' })
    expect(screen.getByText('v2')).toBeInTheDocument()
    expect(trigger).toHaveBeenCalledTimes(1)
    expect(toast.success).not.toHaveBeenCalled()
  })

  it('discovers missing files on a fresh visit and cleans up their records', async () => {
    trigger.mockImplementation(() => ({
      unwrap: () =>
        Promise.resolve({
          objects: [{ ...page.objects[0], verification_status: 'MISSING', provider_object_version: null }],
          total_elements: 1,
        }),
    }))
    renderFirmware()
    expect(await screen.findByText('Missing file')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Clean Up Records' }))
    expect(screen.getByText(/File: file.bin/)).toBeInTheDocument()
    expect(deleteObject).not.toHaveBeenCalled()

    trigger.mockImplementation(() => ({ unwrap: () => Promise.resolve({ objects: [], total_elements: 0 }) }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() => expect(deleteObject).toHaveBeenCalledWith({ object_id: 'one', provider_object_version: null }))
    await waitFor(() => expect(screen.queryByText('v1')).not.toBeInTheDocument())
    expect(toast.success).toHaveBeenCalledWith('Firmware records cleaned up successfully', expect.any(Object))
  })

  it('keeps missing records visible if cleanup detects a reappeared file', async () => {
    trigger.mockImplementation(() => ({
      unwrap: () =>
        Promise.resolve({
          objects: [{ ...page.objects[0], verification_status: 'MISSING', provider_object_version: null }],
          total_elements: 1,
        }),
    }))
    const message = 'The firmware file is present in storage. Refresh the table before deleting it.'
    deleteObject.mockImplementation(() => ({
      unwrap: () => Promise.reject({ status: 409, data: { detail: message } }),
    }))
    renderFirmware()
    fireEvent.click(await screen.findByRole('button', { name: 'Clean Up Records' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(message, expect.any(Object)))
    expect(screen.getByText('v1')).toBeInTheDocument()
    expect(toast.success).not.toHaveBeenCalled()
  })
})
