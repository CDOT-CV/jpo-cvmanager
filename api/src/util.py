from dateutil.parser import parse
import pytz
import os


# expects datetime string
def format_date_utc(d, type="string"):
    if not d:
        return None
    tmp = parse(d)
    utc_tz = tmp.astimezone(pytz.UTC)

    if type.upper() == "STRING":
        return utc_tz.strftime("%Y-%m-%dT%H:%M:%S")
    elif type.upper() == "DATETIME":
        return utc_tz
    else:
        return None


# expects datetime string
def format_date_timezone(d):
    if not d:
        return None
    tmp = parse(d)
    denver_tz = tmp.astimezone(pytz.timezone(os.getenv("TIMEZONE", "America/Denver")))
    return denver_tz.strftime("%m/%d/%Y %I:%M:%S %p")


# expects datetime object
def format_date_timezone_datetime(d):
    if not d:
        return None
    denver_tz = d.astimezone(pytz.timezone(os.getenv("TIMEZONE", "America/Denver")))
    return denver_tz.strftime("%m/%d/%Y %I:%M:%S %p")


# expects datetime string
def format_date_timezone_iso(d):
    if not d:
        return None
    tmp = parse(d)
    denver_tz = tmp.astimezone(pytz.timezone(os.getenv("TIMEZONE", "America/Denver")))
    return denver_tz.isoformat()


# expects datetime, utilizes environment variable to custom timezone
def utc2tz(d):
    if not d:
        return None
    tz_d = d.astimezone(pytz.timezone(os.getenv("TIMEZONE", "America/Denver")))
    return tz_d


# expects datetime object
def format_date_utc_string(d):
    if not d:
        return None
    return d.strftime("%Y-%m-%dT%H:%M:%SZ")
