import { Alert, Button } from '@mui/material'

type StatusRetryAlertProps = {
  statusName: string
  query: {
    isError?: boolean
    isFetching?: boolean
    refetch: () => unknown
  }
}

export const StatusRetryAlert = ({ statusName, query }: StatusRetryAlertProps) => {
  if (!query.isError) return null

  return (
    <Alert severity="warning" action={
      <Button disabled={query.isFetching} onClick={() => query.refetch()}>Retry</Button>
    }>
      {statusName} status unavailable.
    </Alert>
  )
}
