import runpy
from pathlib import Path

import api_environment
import pytest
from werkzeug.exceptions import NotFound


def test_forwarding_routes_removed_with_rsu_features_enabled(monkeypatch):
    monkeypatch.setattr(api_environment, "ENABLE_RSU_FEATURES", True)
    monkeypatch.setattr(api_environment, "ENABLE_ERROR_EMAILS", False)
    main = runpy.run_path(str(Path(__file__).parents[2] / "src" / "main.py"))
    routes = main["app"].url_map.bind("localhost")

    for path in ("/rsu-msgfwd-query", "/rsu-msgfwd-fetch"):
        with pytest.raises(NotFound):
            routes.match(path, method="GET")

    assert routes.match("/rsucounts", method="GET")[0] == "rsuquerycounts"
