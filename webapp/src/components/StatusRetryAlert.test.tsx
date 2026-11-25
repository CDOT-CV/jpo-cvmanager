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
    expect(screen.getByRole('alert')).toHaveTextContent('SCMS update failed')
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(refetch).toHaveBeenCalledOnce()
    view.rerender(<StatusRetryAlert statusName="SCMS" query={{ isError: true, isFetching: true, refetch }} />)
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(screen.getByRole('button', { name: 'Retry' })).toBeDisabled()
    expect(refetch).toHaveBeenCalledOnce()
  })
  it('shows a compact stale warning and prevents retries while offline', () => {
    const refetch = vi.fn()
    render(<StatusRetryAlert statusName="Online" query={{ refetch }} stale compact canRetry={false} />)
    expect(screen.getByRole('alert', { name: 'Online health warning' })).toHaveTextContent('Data may be outdated')
    const retry = screen.getByRole('button', { name: 'Retry' })
    expect(retry).toBeDisabled()
    fireEvent.click(retry)
    expect(refetch).not.toHaveBeenCalled()
  })

})
