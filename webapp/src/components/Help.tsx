import React from 'react'
import '../components/css/Help.css'
import popup from '../icons/rsu_popup_and_config_menu.png'
import status from '../icons/rsu_status.png'
import statusMenu from '../icons/rsu_status_menu.png'
import countMenu from '../icons/rsu_count_menu.png'
import table from '../icons/rsu_count.png'
import filters from '../icons/rsu_filters.png'
import layers from '../icons/rsu_layers.png'
import configure from '../icons/rsu_configure.png'
import EnvironmentVars from '../EnvironmentVars'
import ContactSupportMenu from './ContactSupportMenu'
import { BorderedImage } from '../styles/components/BorderedImage'
import { Stack, Container, useTheme } from '@mui/material'

const Help = () => {
  const theme = useTheme()
  return (
    <Container
      maxWidth={false}
      id="help"
      sx={{ textAlign: 'left', backgroundColor: theme.palette.background.default }}
    >
      <Stack spacing={2}>
        <h2>Welcome to the {EnvironmentVars.DOT_NAME} CV Manager Website</h2>
        <p>
          This application shows the physical location and message counts for each RSU installed by the Colorado
          Department of Transportation at various road sites throughout Colorado.
        </p>

        <p>
          On the map interface, RSUs are represented by colored dots to indicate their current status. A green dot
          means the RSU is online and actively reporting data. A yellow dot indicates the RSU is currently offline but
          was recently active. A red dot shows the RSU is offline and has not reported in some time.
        </p>
        <BorderedImage src={status} alt="RSU Status Indicators on Map" />

        <p>
          Selecting an RSU on the map will open a popup window along with a side configuration menu. The popup displays
          detailed information including the RSU’s IP address, online status, last online time, milepost number, serial
          number, and the number of messages it has reported. The configuration menu allows users, depending on their
          access level, to perform actions such as retrieving the current message forwarding settings, modifying
          configurations, checking firmware updates, applying firmware updates, or rebooting the RSU.
        </p>
        <BorderedImage src={popup} alt="RSU Popup and Configuration Panel" />

        <p>
          On the right side of the interface, there are two menu toggles that open menus with more detailed information
          about the RSUs. The first is the RSU Status Menu, which shows a full list of all RSUs and their current status.
          This menu can be toggled on or off by clicking the red X in the corner or by selecting the "Display RSU Status"
          toggle again. From this menu, users also have the option to print a complete report or an error-specific report
          for further analysis or record-keeping.
        </p>
        <BorderedImage src={statusMenu} alt="RSU Status Menu Display" />

        <p>
          The second menu is the Message Count Menu, which allows users to filter RSU message counts by selecting a
          specific time range and message type. The top date-time picker sets the start of the range, while the bottom
          one sets the end. Below these pickers, a dropdown allows users to choose the type of message they want to track.
          Any change made to the date, time, or message type will automatically update the map and table view to reflect
          the selected parameters.
        </p>
        <BorderedImage src={countMenu} alt="RSU Count Menu" />

        <p>
          A table below the filter menu displays the number of messages received from each RSU within the specified time
          range, regardless of whether the RSU is currently online. The table can be sorted by RSU name, road, or message
          count by clicking on the appropriate column header. Each click toggles between ascending and descending order.
        </p>
        <BorderedImage src={table} alt="RSU Message Count Table" />

        <p>
          The menu on the left side of the screen contains three main sections: Map Layers, RSU Filters, and RSU
          Configuration. The Map Layers section allows users to change how data is visualized. When "RSU Viewer" is
          selected, all RSUs are displayed on the map. Choosing "Heat Map" shows a heatmap based on message counts using
          the filters from the Message Count Menu. The "V2X Message Viewer" and "Moove AI Viewer" options let users query
          specific areas of the map to view corresponding messages or traffic data. Selecting "WZDx Viewer" shows all
          WZDx messages, and the "Intersections" option displays known intersections on the map.
        </p>
        <BorderedImage src={layers} alt="Map Layer Selection Options" />

        <p>
          The RSU Filters section allows users to narrow down the visible RSUs based on vendor and operational status.
          This helps users focus on specific types of hardware or troubleshoot particular categories of RSUs.
        </p>
        <BorderedImage src={filters} alt="RSU Filtering Options" />

        <p>
          The RSU Configuration section enables users to apply configuration changes to multiple RSUs within a selected
          geographic area. This feature streamlines mass updates and management tasks across a network of RSUs.
        </p>
        <BorderedImage src={configure} alt="Bulk RSU Configuration Menu" />
      </Stack>
      <ContactSupportMenu />
    </Container>
  )
}

export default Help