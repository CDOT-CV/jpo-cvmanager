type TimestampedOdeData = {
    timestamp: number
    type: string
    odeData: ProcessedMap | ProcessedSpat | OdeBsmData
}

type TimestampedOdeDataList = TimestampedOdeData[]