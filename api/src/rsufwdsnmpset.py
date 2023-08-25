from marshmallow import Schema, fields
import subprocess
from datetime import datetime
import logging
import snmpcredential
import util
import snmperrorcheck

def ip_to_hex(ip):
  hex_dest_ip = ''
  for octet in ip.split('.'):
    if len(hex(int(octet))[2:]) == 1:
      hex_dest_ip += '0'
    hex_dest_ip += hex(int(octet))[2:]
  return '00000000000000000000FFFF' + hex_dest_ip

# delta is in years
def hex_datetime(now, delta=0):
  # Regex to convert int to hex and to ensure a leading 0 if less than specified length 
  regex = "{0:0{1}x}"
  hex = regex.format(now.year + delta, 4)
  hex += regex.format(now.month, 2)
  hex += regex.format(now.day, 2)
  hex += regex.format(now.hour, 2)
  hex += regex.format(now.minute, 2)
  return hex

def set_rsu_status(rsu_ip, snmp_creds, operate):
  try:
    if operate:
      logging.info(f'Changing RSU status to operate..')
      output = subprocess.run(f'snmpset -v 3 {snmpcredential.get_authstring(snmp_creds)} {rsu_ip} RSU-MIB:rsuMode.0 i 4', shell=True, capture_output=True, check=True)
      output = output.stdout.decode("utf-8").split('\n')[:-1]
    else:
      logging.info(f'Changing RSU status to standby..')
      output = subprocess.run(f'snmpset -v 3 {snmpcredential.get_authstring(snmp_creds)} {rsu_ip} RSU-MIB:rsuMode.0 i 2', shell=True, capture_output=True, check=True)
      output = output.stdout.decode("utf-8").split('\n')[:-1]
    logging.info(f'RSU status change output: {output}')
    return 'success'
  except subprocess.CalledProcessError as e:
    output = e.stderr.decode("utf-8").split('\n')[:-1]
    logging.error(f'Encountered error while changing RSU status: {output[-1]}')
    err_message = snmperrorcheck.check_error_type(output[-1])
    return err_message

def perform_snmp_mods(snmp_mods):
  for snmp_mod in snmp_mods:
    # Perform configuration
    logging.info(f'Running SNMPSET "{snmp_mod}"')
    output = subprocess.run(snmp_mod, shell=True, capture_output=True, check=True)
    output = output.stdout.decode("utf-8").split('\n')[:-1]
    logging.info(f'SNMPSET output: {output}')

def config_msgfwd_yunex(rsu_ip, snmp_creds, dest_ip, udp_port, rsu_index, psid, tx):
  try:
    logging.info('Running SNMP config on Yunex RSU {}'.format(dest_ip))
    
    snmp_mods = []
    authstring = snmpcredential.get_authstring(snmp_creds)

    # Only forward TX messages. Uses rsuXmitMsgFwdingTable table indexes
    # x.1206.4.2.18.20.2.1.2 - hex : PSID
    # x.1206.4.2.18.20.2.1.3 - string : Destination  IP (IPv4)
    # x.1206.4.2.18.20.2.1.4 - int : port
    # x.1206.4.2.18.20.2.1.5 - int : protocol (1: tcp, 2: udp)
    # x.1206.4.2.18.20.2.1.6 - hex : start datetime
    # x.1206.4.2.18.20.2.1.7 - hex : end datetime
    # x.1206.4.2.18.20.2.1.8 - int : Yunex WSMP full message (0: only payload, 1: full message)
    # x.1206.4.2.18.20.2.1.9 - int : SNMP row value (4: create, 6: delete)
    if tx:
      snmp_mod = 'snmpset -v 3 {auth} {rsuip} '.format(auth=authstring, rsuip=rsu_ip)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.20.2.1.2.{index} x {msgpsid} '.format(index=rsu_index, msgpsid=psid)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.20.2.1.3.{index} s {destip} '.format(index=rsu_index, destip=dest_ip)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.20.2.1.4.{index} i {port} '.format(index=rsu_index, port=udp_port)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.20.2.1.5.{index} i 2 '.format(index=rsu_index)

      # Yunex expects a hex value of 16 length for rsuXmitMsgFwdingTable
      now = util.utc2tz(datetime.now())
      start_hex = hex_datetime(now) + '0000'
      end_hex = hex_datetime(now, 10) + '0000'

      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.20.2.1.6.{index} x {dt} '.format(index=rsu_index, dt=start_hex)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.20.2.1.7.{index} x {dt} '.format(index=rsu_index, dt=end_hex)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.20.2.1.8.{index} i 0 '.format(index=rsu_index)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.20.2.1.9.{index} i 4'.format(index=rsu_index)
      
      snmp_mods.append(snmp_mod)
    # Only forward RX messages. Uses rsuReceivedMsgTable table indexes
    # x.1206.4.2.18.5.2.1.2 - hex : PSID
    # x.1206.4.2.18.5.2.1.3 - string : Destination  IP (IPv4)
    # x.1206.4.2.18.5.2.1.4 - int : port
    # x.1206.4.2.18.5.2.1.5 - int : protocol (1: tcp, 2: udp)
    # x.1206.4.2.18.5.2.1.6 - int - rssi (-100 is recommended)
    # x.1206.4.2.18.5.2.1.7 - int : message forward rate (forward every nth message)
    # x.1206.4.2.18.5.2.1.8 - hex : start datetime
    # x.1206.4.2.18.5.2.1.9 - hex : end datetime
    # x.1206.4.2.18.5.2.1.10 - int : SNMP row value (4: create, 6: delete)
    # x.1206.4.2.18.5.2.1.11 - int : Yunex WSMP full message (0: only payload, 1: full message)
    # x.1206.4.2.18.5.2.1.12 - int : Yunex feature. Do not turn on. (0: off, 1: on)
    else:
      snmp_mod = 'snmpset -v 3 {auth} {rsuip} '.format(auth=authstring, rsuip=rsu_ip)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.2.{index} x {msgpsid} '.format(index=rsu_index, msgpsid=psid)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.3.{index} s {destip} '.format(index=rsu_index, destip=dest_ip)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.4.{index} i {port} '.format(index=rsu_index, port=udp_port)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.5.{index} i 2 '.format(index=rsu_index)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.6.{index} i -100 '.format(index=rsu_index)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.7.{index} i 1 '.format(index=rsu_index)

      # Yunex expects a hex value of 16 length for rsuReceivedMsgTable
      now = util.utc2tz(datetime.now())
      start_hex = hex_datetime(now) + '0000'
      end_hex = hex_datetime(now, 10) + '0000'

      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.8.{index} x {dt} '.format(index=rsu_index, dt=start_hex)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.9.{index} x {dt} '.format(index=rsu_index, dt=end_hex)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.10.{index} i 4 '.format(index=rsu_index)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.11.{index} i 0 '.format(index=rsu_index)
      snmp_mod += '1.3.6.1.4.1.1206.4.2.18.5.2.1.12.{index} i 0'.format(index=rsu_index)

      snmp_mods.append(snmp_mod)

    perform_snmp_mods(snmp_mods)
    response = "Successfully completed the Yunex SNMPSET configuration"
    code = 200
  except subprocess.CalledProcessError as e:
    output = e.stderr.decode("utf-8").split('\n')[:-1]
    logging.error(f'Encountered error while modifying Yunex RSU SNMP: {output[-1]}')
    response = snmperrorcheck.check_error_type(output[-1])
    code = 500

  return response, code

def config_msgfwd(rsu_ip, manufacturer, snmp_creds, dest_ip, udp_port, rsu_index, psid, raw = False):
  try:
    # Put RSU in standby
    rsu_mod_result = set_rsu_status(rsu_ip, snmp_creds, operate=False)
    if rsu_mod_result != 'success':
      return rsu_mod_result, 500
    
    # Create a hex version of destIP using the specified endian type
    hex_dest_ip = ip_to_hex(dest_ip)
    
    logging.info('Running SNMP config on {}'.format(rsu_ip))
    logging.debug(f'SNMP config: Manufacturer:{manufacturer}, Destination:{dest_ip}:{udp_port}, Hex_PSID:{hex_dest_ip}')
    
    snmp_mods = []
    authstring = snmpcredential.get_authstring(snmp_creds)
    # Raw is for running the SNMP commands without the RSU 4.1 spec
    if not raw:
      snmp_mod = 'snmpset -v 3 {auth} {rsuip} '.format(auth=authstring, rsuip=rsu_ip)
      snmp_mod += 'RSU-MIB:rsuDsrcFwdStatus.{index} i 4 '.format(index=rsu_index)
      snmp_mod += 'RSU-MIB:rsuDsrcFwdPsid.{index} x {msgpsid} '.format(index=rsu_index, msgpsid=psid)
      snmp_mod += 'RSU-MIB:rsuDsrcFwdDestIpAddr.{index} x {destip} '.format(index=rsu_index, destip=hex_dest_ip)
      snmp_mod += 'RSU-MIB:rsuDsrcFwdDestPort.{index} i {port} '.format(index=rsu_index, port=udp_port)
      snmp_mod += 'RSU-MIB:rsuDsrcFwdProtocol.{index} i 2 '.format(index=rsu_index)
      snmp_mod += 'RSU-MIB:rsuDsrcFwdRssi.{index} i -100 '.format(index=rsu_index)
      snmp_mod += 'RSU-MIB:rsuDsrcFwdMsgInterval.{index} i 1 '.format(index=rsu_index)
      # Start datetime, hex of the current time (timezone based on the manufacturer)
      if manufacturer == 'Commsignia':
        # Configured timezone
        now = util.utc2tz(datetime.now())
      else:
        # UTC
        now = datetime.now()
      snmp_mod += 'RSU-MIB:rsuDsrcFwdDeliveryStart.{index} x {dt} '.format(index=rsu_index, dt=hex_datetime(now))
      # Stop datetime, hex of the current time + 10 years in the future
      snmp_mod += 'RSU-MIB:rsuDsrcFwdDeliveryStop.{index} x {dt} '.format(index=rsu_index, dt=hex_datetime(now, 10))
      snmp_mod += 'RSU-MIB:rsuDsrcFwdEnable.{index} i 1'.format(index=rsu_index)
      snmp_mods.append(snmp_mod)
    else:
      # Commands must be run individually to be run without the RSU 4.1 spec
      # This must be done when configuring MAP, SSM and SRM because their PSIDs are not compatible with the spec
      snmp_mods.append('snmpset -v 3 {auth} {rsuip} 1.0.15628.4.1.7.1.2.{index} x {msgpsid}'.format(auth=authstring, rsuip=rsu_ip, index=rsu_index, msgpsid=psid))
      snmp_mods.append('snmpset -v 3 {auth} {rsuip} 1.0.15628.4.1.7.1.3.{index} x {destip}'.format(auth=authstring, rsuip=rsu_ip, index=rsu_index, destip=hex_dest_ip))
      snmp_mods.append('snmpset -v 3 {auth} {rsuip} 1.0.15628.4.1.7.1.4.{index} i {port}'.format(auth=authstring, rsuip=rsu_ip, index=rsu_index, port=udp_port))
      snmp_mods.append('snmpset -v 3 {auth} {rsuip} 1.0.15628.4.1.7.1.5.{index} i 2'.format(auth=authstring, rsuip=rsu_ip, index=rsu_index))
      snmp_mods.append('snmpset -v 3 {auth} {rsuip} 1.0.15628.4.1.7.1.6.{index} i -100'.format(auth=authstring, rsuip=rsu_ip, index=rsu_index))
      snmp_mods.append('snmpset -v 3 {auth} {rsuip} 1.0.15628.4.1.7.1.7.{index} i 1'.format(auth=authstring, rsuip=rsu_ip, index=rsu_index))
      # Start datetime, hex of the current time (timezone based on the manufacturer)
      if manufacturer == 'Commsignia':
        # Configured timezone
        now = util.utc2tz(datetime.now())
      else:
        # UTC
        now = datetime.now()
      snmp_mods.append('snmpset -v 3 {auth} {rsuip} 1.0.15628.4.1.7.1.8.{index} x {dt}'.format(auth=authstring, rsuip=rsu_ip, index=rsu_index, dt=hex_datetime(now)))
      # Stop datetime, hex of the current time + 10 years in the future
      snmp_mods.append('snmpset -v 3 {auth} {rsuip} 1.0.15628.4.1.7.1.9.{index} x {dt}'.format(auth=authstring, rsuip=rsu_ip, index=rsu_index, dt=hex_datetime(now, 10)))
      snmp_mods.append('snmpset -v 3 {auth} {rsuip} 1.0.15628.4.1.7.1.10.{index} i 1'.format(auth=authstring, rsuip=rsu_ip, index=rsu_index))
      snmp_mods.append('snmpset -v 3 {auth} {rsuip} 1.0.15628.4.1.7.1.11.{index} i 4'.format(auth=authstring, rsuip=rsu_ip, index=rsu_index))

    perform_snmp_mods(snmp_mods)
    response = "Successfully completed the rsuDsrcFwd SNMPSET configuration"
    code = 200
  except subprocess.CalledProcessError as e:
    output = e.stderr.decode("utf-8").split('\n')[:-1]
    logging.error(f'Encountered error while modifying RSU SNMP: {output[-1]}')
    response = snmperrorcheck.check_error_type(output[-1])
    code = 500
  finally:
    # Put RSU in run mode, this doesn't need to be captured
    # If the previous commands work, this should work
    # If the previous commands fail, this will probably fail 
    # and we want to preserve the previous failure as the return message
    set_rsu_status(rsu_ip, snmp_creds, operate=True)

  return response, code

def config_del(rsu_ip, manufacturer, snmp_creds, msg_type, rsu_index):
  if manufacturer == 'Kapsch' or manufacturer == 'Commsignia':
    try:
      # Put RSU in standby
      rsu_mod_result = set_rsu_status(rsu_ip, snmp_creds, operate=False)
      if rsu_mod_result != 'success':
        return rsu_mod_result, 500

      snmp_mods = 'snmpset -v 3 {auth} {rsuip} '.format(auth=snmpcredential.get_authstring(snmp_creds), rsuip=rsu_ip)
      snmp_mods += 'RSU-MIB:rsuDsrcFwdStatus.{index} i 6 '.format(index=rsu_index)

      # Perform configurations
      logging.info(f'Running SNMPSET deletion "{snmp_mods}"')
      output = subprocess.run(snmp_mods, shell=True, capture_output=True, check=True)
      output = output.stdout.decode("utf-8").split('\n')[:-1]
      logging.info(f'SNMPSET output: {output}')

      response = "Successfully deleted the rsuDsrcFwd SNMPSET configuration"
      code = 200
    except subprocess.CalledProcessError as e:
      output = e.stderr.decode("utf-8").split('\n')[:-1]
      logging.error(f'Encountered error while deleting RSU SNMP config: {output[-1]}')
      response = snmperrorcheck.check_error_type(output[-1])
      code = 500
    finally:
      # Put RSU in run mode, this doesn't need to be captured
      # If the previous commands work, this should work
      # If the previous commands fail, this will probably fail 
      # and we want to preserve the previous failure as the return message
      set_rsu_status(rsu_ip, snmp_creds, operate=True)
  elif manufacturer == 'Yunex':
    try:
      snmp_mods = 'snmpset -v 3 {auth} {rsuip} '.format(auth=snmpcredential.get_authstring(snmp_creds), rsuip=rsu_ip)
      if msg_type.lower() == 'bsm':
        snmp_mods += '1.3.6.1.4.1.1206.4.2.18.5.2.1.10.{index} i 6 '.format(index=rsu_index)
      if msg_type.lower() == 'spat':
        snmp_mods += '1.3.6.1.4.1.1206.4.2.18.20.2.1.9.{index} i 6 '.format(index=rsu_index)
      if msg_type.lower() == 'map':
        snmp_mods += '1.3.6.1.4.1.1206.4.2.18.20.2.1.9.{index} i 6 '.format(index=rsu_index)
      if msg_type.lower() == 'ssm':
        snmp_mods += '1.3.6.1.4.1.1206.4.2.18.20.2.1.9.{index} i 6 '.format(index=rsu_index)
      if msg_type.lower() == 'srm':
        snmp_mods += '1.3.6.1.4.1.1206.4.2.18.5.2.1.10.{index} i 6 '.format(index=rsu_index)
      
      # Perform configurations
      logging.info(f'Running SNMPSET deletion "{snmp_mods}"')
      output = subprocess.run(snmp_mods, shell=True, capture_output=True, check=True)
      output = output.stdout.decode("utf-8").split('\n')[:-1]
      logging.info(f'SNMPSET output: {output}')

      response = "Successfully deleted the Yunex SNMPSET configuration"
      code = 200
    except subprocess.CalledProcessError as e:
      print("output",e.stderr.decode("utf-8").split('\n'))
      output = e.stderr.decode("utf-8").split('\n')[:-1]
      
      logging.error(f'Encountered error while deleting Yunex RSU SNMP config: {output[-1]}')
      response = snmperrorcheck.check_error_type(output[-1])
      code = 500
  else:
    response = "Supported RSU manufacturers are currently only Commsignia, Kapsch and Yunex"
    code = 501
  
  return response, code

def config_init(rsu_ip, manufacturer, snmp_creds, dest_ip, msg_type, index):
  # Based on manufacturer, choose the right function call
  if manufacturer == 'Kapsch' or manufacturer == 'Commsignia':
    # Based on message type, choose the right port
    if msg_type.lower() == 'bsm':
      return config_msgfwd(rsu_ip, manufacturer, snmp_creds, dest_ip, '46800', index, '20')
    if msg_type.lower() == 'spat':
      return config_msgfwd(rsu_ip, manufacturer, snmp_creds, dest_ip, '44910', index, '8002')
    if msg_type.lower() == 'map':
      return config_msgfwd(rsu_ip, manufacturer, snmp_creds, dest_ip, '44920', index, 'E0000017', raw=True)
    if msg_type.lower() == 'ssm':
      return config_msgfwd(rsu_ip, manufacturer, snmp_creds, dest_ip, '44900', index, 'E0000015', raw=True)
    if msg_type.lower() == 'srm':
      return config_msgfwd(rsu_ip, manufacturer, snmp_creds, dest_ip, '44930', index, 'E0000016', raw=True)
    if msg_type.lower() == 'psm':
      return config_msgfwd(rsu_ip, manufacturer, snmp_creds, dest_ip, '44940', index, '27')
    if msg_type.lower() == 'tim':
      return config_msgfwd(rsu_ip, manufacturer, snmp_creds, dest_ip, '47900', index, '8003')
    else:
      return "Supported message type is currently only BSM, SPaT, MAP, SSM SRM, PSM, and TIM", 501
  elif manufacturer == 'Yunex':
    # Based on message type, choose the right port
    if msg_type.lower() == 'bsm':
      return config_msgfwd_yunex(rsu_ip, snmp_creds, dest_ip, '46800', index, '20', False)
    if msg_type.lower() == 'spat':
      return config_msgfwd_yunex(rsu_ip, snmp_creds, dest_ip, '44910', index, '8002', True)
    if msg_type.lower() == 'map':
      return config_msgfwd_yunex(rsu_ip, snmp_creds, dest_ip, '44920', index, 'E0000017', True)
    if msg_type.lower() == 'ssm':
      return config_msgfwd_yunex(rsu_ip, snmp_creds, dest_ip, '44900', index, 'E0000015', True)
    if msg_type.lower() == 'srm':
      return config_msgfwd_yunex(rsu_ip, snmp_creds, dest_ip, '44930', index, 'E0000016', False)
    if msg_type.lower() == 'psm':
      return config_msgfwd_yunex(rsu_ip, snmp_creds, dest_ip, '44940', index, '27', False)
    if msg_type.lower() == 'tim':
      return config_msgfwd_yunex(rsu_ip, snmp_creds, dest_ip, '47900', index, '8003', True)
    else:
      return "Supported message type is currently only BSM, SPaT, MAP, SSM, SRM, PSM, and TIM", 501
  else:
    return "Supported RSU manufacturers are currently only Commsignia, Kapsch and Yunex", 501

class SnmpsetSchema(Schema):
  dest_ip = fields.IPv4(required=True)
  msg_type = fields.Str(required=True)
  rsu_index = fields.Int(required=True)

def post(request):
  logging.info(f'Running command, POST rsuFwdSnmpset')
  # Check if the args match what is needed for the snmpset command
  schema = SnmpsetSchema()
  errors = schema.validate(request['args'])
  if errors:
    return f"The provided args does not match required values: {str(errors)}", 400

  response, code = config_init(request['rsu_ip'], request['manufacturer'], request['snmp_creds'], request['args']['dest_ip'], request['args']['msg_type'], request['args']['rsu_index'])
  return { "RsuFwdSnmpset": response }, code

class SnmpsetDeleteSchema(Schema):
  msg_type = fields.Str(required=True)
  rsu_index = fields.Int(required=True)

def delete(request):
  logging.info(f'Running command, DELETE rsuFwdSnmpset')
  # Check if the args match what is needed for the snmpset command
  schema = SnmpsetDeleteSchema()
  errors = schema.validate(request['args'])
  if errors:
    return f"The provided args does not match required values: {str(errors)}", 400
  
  response, code = config_del(request['rsu_ip'], request['manufacturer'], request['snmp_creds'], request['args']['msg_type'], request['args']['rsu_index'])
  return { "RsuFwdSnmpset": response }, code