import React from 'react'

import {
  Box,
  Card,
  Checkbox,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Button
} from '@mui/material'
import { DecoderEntry } from './decoder-entry'
import DownloadIcon from '@mui/icons-material/Download'
import { useDispatch, useSelector } from 'react-redux'
import {
  onPcapFileUploaded,
  decodeAllToJson,
  selectPcapData,
  selectPcapDataStats,
  selectDecodedJsonData,
  selectUniqueMaps,
} from './pcap-decoder-slice'
import { ThunkDispatch, AnyAction } from '@reduxjs/toolkit'
import { RootState } from '../../../store'

export const PcapTables = () => {
  const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()

  const pcapData = useSelector(selectPcapData)
  const pcapDataStats = useSelector(selectPcapDataStats)
  const decodedJsonData = useSelector(selectDecodedJsonData)
  const uniqueMaps = useSelector(selectUniqueMaps)

  const pcapContents = Object.values(pcapData)
 
  const pcapFileUploaded = (event) => {
    console.debug("pcapFileUploaded")
    const file = event.target.files[0]
    if (file) {
      const reader = new FileReader()
      reader.onload = function (evt) {
        console.debug("pcapFileUploaded: reader.onload")
        try {
          const bytes = evt.target?.result as ArrayBuffer
          console.debug("Got an ArrayBuffer with " + bytes.byteLength + " bytes")
          dispatch(onPcapFileUploaded(bytes))
        } catch (e) {
          console.error('Error reading uploaded pcap file', e)
        }
      }
      reader.readAsArrayBuffer(file)
    }
  }

  

  const handleDownloadHexClick = () => {
    downloadJsonFile(pcapContents, "hex.json")
  }

  const handleDownloadJsonClick = () => {
    downloadJsonFile(decodedJsonData, "all-messages.json")
  }

  

  const downloadJsonFile = (contents: any, name: string, alreadyStringified = false) => {
    const element = document.createElement('a')
    const file = new Blob([alreadyStringified ? contents : JSON.stringify(contents)], {
      type: 'application/json',
    })
    element.href = URL.createObjectURL(file)
    element.download = name
    document.body.appendChild(element) // Required for this to work in FireFox
    element.click()
  }

  const handleCheckboxChange = (intersectionId) => {
    console.log("Checkbox changed for intersection " + intersectionId);
  }


  return (
    <Box>
      <Typography sx={{ m: 1 }} variant="h6" color="white">
        Upload a PCAP file:
        <input type="file" 
        onChange={ (event) => pcapFileUploaded(event) } 
        title="Upload PCAP File"/>
      </Typography>
        
      {pcapDataStats?.totalCount > 0 &&
        <Box sx={{ margin: '5px' }}>
          <IconButton aria-label="download-hex" size="small"
            onClick={() => handleDownloadHexClick()}>
            <DownloadIcon/>Download ASN.1 hex
          </IconButton>
            
          <IconButton aria-label="download-json" size="small"
            onClick={() => handleDownloadJsonClick()}>
            <DownloadIcon/>Download Decoded JSON
          </IconButton>
          
          <Card>
            <Typography sx={{ m: 1 }} variant="h6">
              Statistics
            </Typography>
            <TableContainer>
              <TableHead>
                <TableRow>
                  <TableCell>First Timestamp</TableCell>
                  <TableCell>Last Timestmap</TableCell>
                  <TableCell>MAPs</TableCell>
                  <TableCell>Unique MAPs</TableCell>
                  <TableCell>SPATs</TableCell>
                  <TableCell>BSMs</TableCell>
                  <TableCell>SSMs</TableCell>
                  <TableCell>SRMs</TableCell>
                  <TableCell>Unknown</TableCell>
                  <TableCell>Total</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                <TableRow>
                  <TableCell>{new Date(pcapDataStats.firstTimestamp).toISOString()}</TableCell>
                  <TableCell>{new Date(pcapDataStats.lastTimestamp).toISOString()}</TableCell>
                  <TableCell>{pcapDataStats.mapCount}</TableCell>
                  <TableCell>{pcapDataStats.uniqueMapCount}</TableCell>
                  <TableCell>{pcapDataStats.spatCount}</TableCell>
                  <TableCell>{pcapDataStats.bsmCount}</TableCell>
                  <TableCell>{pcapDataStats.ssmCount}</TableCell>
                  <TableCell>{pcapDataStats.srmCount}</TableCell>
                  <TableCell>{pcapDataStats.unknownCount}</TableCell>
                  <TableCell>{pcapDataStats.totalCount}</TableCell>
                </TableRow>
              </TableBody>
            </TableContainer>
          </Card>

        
        </Box>
      }

      {uniqueMaps?.length > 0 &&
        <Card>
          <Typography sx={{ m: 1 }} variant="h6">
            Unique MAP Messages
          </Typography>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Select</TableCell>
                  <TableCell>Intersection ID</TableCell>
                  <TableCell>Intersection Name</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
              {uniqueMaps.map((mapMsg) => {
                  console.log(mapMsg)
                    return (
                      <TableRow>
                        <TableCell>
                          <Button size="small"
                              variant="contained"
                              onClick={() => handleCheckboxChange(mapMsg?.properties?.intersectionId)}
                            >Show</Button>
                        </TableCell>
                        <TableCell>{mapMsg?.properties?.intersectionId}</TableCell>
                        <TableCell>{mapMsg?.properties?.intersectionName}</TableCell>
                      </TableRow>
                    )
                  }
                )
              }
              </TableBody>
            </Table>
          </TableContainer>
        </Card>
      }
    </Box>
  )
}
