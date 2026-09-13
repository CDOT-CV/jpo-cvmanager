import { MessageCount } from '../../models/MessageCount'

export type RsuCountTableRow = {
  messageType: string
  odeInputCount: number
  odeOutputCount: number
}

export const formatCount = (value: number) => value.toLocaleString('en-US')

export const buildRsuCountTableRows = (
  messageTypes: string[],
  counts: MessageCount[] | undefined
): RsuCountTableRow[] => {
  const countsByType = new Map((counts ?? []).map((count) => [count.message_type?.toUpperCase(), count] as const))

  return messageTypes.map((type) => {
    const count = countsByType.get(type.toUpperCase())
    return {
      messageType: type.toUpperCase(),
      odeInputCount: count?.ode_input_count ?? 0,
      odeOutputCount: count?.ode_output_count ?? 0,
    }
  })
}
