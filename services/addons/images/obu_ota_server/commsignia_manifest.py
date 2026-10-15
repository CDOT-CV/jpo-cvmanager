import os
import datetime
import re
from typing import List, NamedTuple
import copy

# The manifest page size that Commsignia OBUs have historically received. Larger
# catalogs grow the page so every firmware fits in a single response.
MIN_PAGE_SIZE = 32

document = {
    "content": [],
    "pageable": {
        "sort": {"empty": False, "unsorted": False, "sorted": True},
        "offset": 0,
        "pageNumber": 0,
        "pageSize": MIN_PAGE_SIZE,
        "paged": True,
        "unpaged": False,
    },
    "last": True,
    "totalPages": 1,
    "totalElements": 0,
    "size": MIN_PAGE_SIZE,
    "number": 0,
    "sort": {"empty": False, "unsorted": False, "sorted": True},
    "numberOfElements": 0,
    "first": True,
    "empty": False,
}


FIRMWARE_EXTENSION = ".tar.sig"
# Matches the obu_ota_requests.requested_firmware_version column size
MAX_FIRMWARE_NAME_LENGTH = 128
FIRMWARE_NAME_CHARACTERS = re.compile(r"[A-Za-z0-9._-]+")
DEVICE_TYPES = {"ob4": "OBU", "rs4": "RSU"}
ACCESS_MODES = {"rw": True, "ro": False}
SECURE_BOOT_MARKER = "secureboot"


class FirmwareName(NamedTuple):
    variant: str
    device_type: str
    writable: bool
    secure: bool
    release: str


def _is_release(value: str) -> bool:
    return value[:1].isascii() and value[:1].isalnum()


def parse_firmware_name(file_name: str) -> FirmwareName | None:
    """Parses a Commsignia firmware file name.

    Names follow <variant>-<rw|ro>[-secureboot]-<release>.tar.sig, where the
    variant is ob4-generic or rs4-generic with an optional suffix. A matching
    name is a single path component, so it can be used safely as a file name.
    When a name contains more than one rw/ro marker, the last one that leaves a
    valid release is used, so the variant is as long as possible.

    The name is split into tokens instead of matched with one regular
    expression, so the cost of parsing grows linearly with the name length.
    """
    if len(file_name) > MAX_FIRMWARE_NAME_LENGTH or not file_name.endswith(FIRMWARE_EXTENSION):
        return None
    stem = file_name[: -len(FIRMWARE_EXTENSION)]
    if not FIRMWARE_NAME_CHARACTERS.fullmatch(stem):
        return None

    tokens = stem.split("-")
    if len(tokens) < 4 or tokens[0] not in DEVICE_TYPES or tokens[1] != "generic":
        return None

    for marker in range(len(tokens) - 2, 1, -1):
        if tokens[marker] not in ACCESS_MODES:
            continue
        # A variant suffix after "-generic" must not be empty
        if marker > 2 and not "-".join(tokens[2:marker]):
            continue

        variant = "-".join(tokens[:marker])
        writable = ACCESS_MODES[tokens[marker]]
        if tokens[marker + 1] == SECURE_BOOT_MARKER:
            release = "-".join(tokens[marker + 2 :])
            if _is_release(release):
                return FirmwareName(variant, DEVICE_TYPES[tokens[0]], writable, True, release)
        release = "-".join(tokens[marker + 1 :])
        if _is_release(release):
            return FirmwareName(variant, DEVICE_TYPES[tokens[0]], writable, False, release)
    return None


def is_valid_firmware_name(file_name: str) -> bool:
    return parse_firmware_name(file_name) is not None


def add_contents(server: str, firmware_list: List) -> dict:
    manifest = copy.deepcopy(document)

    for firmware in firmware_list:

        fname = firmware.split("/")[-1]
        info = parse_firmware_name(fname)
        if not info:
            continue

        content = {}

        content["id"] = ""
        content["name"] = fname
        content["variant"] = info.variant
        content["releaseVersion"] = info.release
        content["type"] = info.device_type
        content["writableSystemPartition"] = info.writable
        content["secure"] = info.secure
        content["uploadedAt"] = datetime.datetime.fromtimestamp(
            os.path.getmtime(firmware), tz=datetime.timezone.utc
        ).strftime("%Y-%m-%dT%H:%M:%SZ")
        content["size"] = os.stat(firmware).st_size
        content["links"] = [
            {
                "rel": "local-file",
                "href": server + "/firmwares/commsignia/" + fname,
                "type": "application/octet-stream",
            }
        ]
        manifest["content"].append(content)

    count = len(manifest["content"])
    page_size = max(MIN_PAGE_SIZE, count)
    manifest["totalElements"] = count
    manifest["numberOfElements"] = count
    manifest["size"] = page_size
    manifest["pageable"]["pageSize"] = page_size
    manifest["empty"] = count == 0
    return manifest
