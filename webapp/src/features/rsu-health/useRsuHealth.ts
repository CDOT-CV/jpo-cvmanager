import { useEffect, useState } from 'react'
import { useSelector } from 'react-redux'
import EnvironmentVars from '../../EnvironmentVars'
import { selectToken } from '../../generalSlices/userSlice'
import { useGetRsuLastOnlineQuery, useGetRsuOnlineStatusesQuery } from '../api/rsuOnlineStatusApiSlice'
import { useGetScmsStatusQuery } from '../api/scmsApiSlice'
import { describeHealthFetch, RSU_HEALTH_REFRESH } from './refresh-policy'

const readActivity = () => ({
  visible: document.visibilityState !== 'hidden',
  online: navigator.onLine !== false,
  now: Date.now(),
})

// One policy for every health consumer. RTK Query shares requests by endpoint and argument.
export const useRsuHealth = (organization: string, ip = '') => {
  const token = useSelector(selectToken)
  const enabled = Boolean(organization && token && EnvironmentVars.ENABLE_RSU_FEATURES)
  const [activity, setActivity] = useState(readActivity)
  useEffect(() => {
    const update = () => setActivity(readActivity())
    document.addEventListener('visibilitychange', update)
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    const timer = enabled ? window.setInterval(() => {
      if (document.visibilityState !== 'hidden') update()
    }, RSU_HEALTH_REFRESH.clockTick) : undefined
    return () => {
      document.removeEventListener('visibilitychange', update)
      window.removeEventListener('online', update)
      window.removeEventListener('offline', update)
      window.clearInterval(timer)
    }
  }, [enabled])

  const options = (interval: number) => ({
    skip: !enabled,
    pollingInterval: enabled && activity.visible && activity.online ? interval : 0,
    refetchOnFocus: true,
    refetchOnReconnect: true,
    refetchOnMountOrArgChange: interval / 1000,
  })
  const onlineStatusQuery = useGetRsuOnlineStatusesQuery(organization, options(RSU_HEALTH_REFRESH.online))
  const scmsStatusQuery = useGetScmsStatusQuery(organization, options(RSU_HEALTH_REFRESH.scms))
  const lastOnlineQuery = useGetRsuLastOnlineQuery(ip, {
    ...options(RSU_HEALTH_REFRESH.lastOnline),
    skip: !enabled || !ip,
  })
  const feeds = [
    { label: 'Online', query: onlineStatusQuery, interval: RSU_HEALTH_REFRESH.online },
    { label: 'SCMS', query: scmsStatusQuery, interval: RSU_HEALTH_REFRESH.scms },
    ...(ip ? [{ label: 'Last online', query: lastOnlineQuery, interval: RSU_HEALTH_REFRESH.lastOnline }] : []),
  ]
  const refreshing = feeds.some(({ query }) => query.isFetching)
  const summary = feeds.map(({ label, query, interval }) => describeHealthFetch(label, query, activity.now, interval))
  return {
    onlineStatusQuery, scmsStatusQuery, lastOnlineQuery, feeds, refreshing, summary,
    canRetry: enabled && activity.online, networkOnline: activity.online, now: activity.now,
  }
}
