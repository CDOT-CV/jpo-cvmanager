import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { StatusRetryAlert } from './StatusRetryAlert'

describe('StatusRetryAlert', () => {
  it('renders no wrapper when the query has not failed', () => {
    const { container } = render(<StatusRetryAlert statusName="Online" query={{ refetch: vi.fn() }} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('identifies the failed feed and disables Retry until its request finishes', () => {
    const refetch = vi.fn()
    const view = render(<StatusRetryAlert statusName="SCMS" query={{ isError: true, refetch }} />)
    expect(screen.getByRole('alert')).toHaveTextContent('SCMS status unavailable.')
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(refetch).toHaveBeenCalledOnce()
    view.rerender(<StatusRetryAlert statusName="SCMS" query={{ isError: true, isFetching: true, refetch }} />)
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(screen.getByRole('button', { name: 'Retry' })).toBeDisabled()
    expect(refetch).toHaveBeenCalledOnce()
  })
})
