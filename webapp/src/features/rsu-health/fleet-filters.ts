import { useMemo } from 'react'
import type { RsuInfo } from '../../models/RsuApi'
import { getOnlineStatus, getScmsStatus, type OnlineStatus, type ScmsStatus } from '../../utils/rsu-status'

export const attentionGroups = [
  { id: 'offline', label: 'Offline', matches: (online: OnlineStatus) => online === 'offline' },
  { id: 'unstable', label: 'Unstable', matches: (online: OnlineStatus) => online === 'unstable' },
  { id: 'certificate', label: 'SCMS Unhealthy', matches: (_online: OnlineStatus, scms: ScmsStatus) => scms === 'unhealthy' },
  { id: 'unknown', label: 'Unknown', matches: (online: OnlineStatus, scms: ScmsStatus) => online === 'unknown' || scms === 'unknown' },
] as const

export type FleetFilters = {
  search: string
  vendor: string
  attention: '' | typeof attentionGroups[number]['id']
}

export const emptyFleetFilters: FleetFilters = { search: '', vendor: '', attention: '' }

const noRsus: RsuInfo[] = []

const normalizeSearch = (text: string) => text.toLowerCase().replace(/-/g, '')

export const useFleetDevices = (
  rsus: RsuInfo[] = noRsus,
  filters: FleetFilters,
  onlineQuery: Parameters<typeof getOnlineStatus>[1],
  scmsQuery: Parameters<typeof getScmsStatus>[1]
) => {
  return useMemo(() => {
    const devices = rsus.map((rsu) => {
      const ip = rsu.properties.ipv4_address
      return { rsu, online: getOnlineStatus(ip, onlineQuery), scms: getScmsStatus(ip, scmsQuery) }
    })
    const terms = normalizeSearch(filters.search).trim().split(/\s+/).filter(Boolean)
    const candidates = devices.filter(({ rsu: { properties } }) => {
      if (filters.vendor && properties.manufacturer_name !== filters.vendor) return false

      const searchable = normalizeSearch(
        [properties.ipv4_address, properties.primary_route, 'Milepost', 'Mile', properties.milepost].join(' ')
      )
      return terms.every((term) => searchable.includes(term))
    })
    const matches = (group: typeof attentionGroups[number], device: typeof devices[number]) =>
      group.matches(device.online, device.scms)
    const selected = attentionGroups.find(({ id }) => id === filters.attention)

    return {
      devices,
      visibleDevices: selected ? candidates.filter((device) => matches(selected, device)) : candidates,
      counts: attentionGroups.map((group) => ({
        ...group,
        count: candidates.filter((device) => matches(group, device)).length,
      })),
      vendors: [...new Set(rsus.map(({ properties }) => properties.manufacturer_name).filter(Boolean))].sort(),
    }
  }, [rsus, filters, onlineQuery, scmsQuery])
}
