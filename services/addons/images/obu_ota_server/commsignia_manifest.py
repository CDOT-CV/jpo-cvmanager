import os
import datetime
import re
from typing import List
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


# Anchored and restricted to a single path component so a matching name can be
# used safely as a file name
magic_regex = re.compile(
    r"^(?P<variant>(?P<type>ob4|rs4)-generic(?:-[A-Za-z0-9._-]+)?)-(?:(?P<writable>rw)|ro)(?P<secure>-secureboot)?-(?P<release>[A-Za-z0-9][A-Za-z0-9._-]*)\.tar\.sig$"
)


def is_valid_firmware_name(file_name: str) -> bool:
    return magic_regex.fullmatch(file_name) is not None


def add_contents(server: str, firmware_list: List) -> dict:
    manifest = copy.deepcopy(document)

    for firmware in firmware_list:

        fname = firmware.split("/")[-1]
        info = magic_regex.fullmatch(fname)
        if not info:
            continue

        content = {}

        content["id"] = ""
        content["name"] = fname
        content["variant"] = info["variant"]
        content["releaseVersion"] = info["release"]
        content["type"] = "RSU" if info["type"] == "rs4" else "OBU"
        content["writableSystemPartition"] = bool(info["writable"])
        content["secure"] = bool(info["secure"])
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
