import pytest
from cryptography.fernet import Fernet

from addons.images.iss_health_check import iss_health_check_environment

KEY_1 = Fernet.generate_key().decode()
KEY_2 = Fernet.generate_key().decode()


def test_parse_encryption_keys_single():
    assert iss_health_check_environment.parse_encryption_keys(KEY_1) == [KEY_1]


def test_parse_encryption_keys_multiple():
    actual_value = iss_health_check_environment.parse_encryption_keys(
        f" {KEY_1} , {KEY_2} "
    )
    assert actual_value == [KEY_1, KEY_2]


def test_parse_encryption_keys_empty():
    with pytest.raises(ValueError, match="at least one key"):
        iss_health_check_environment.parse_encryption_keys(" , ")


def test_parse_encryption_keys_invalid():
    with pytest.raises(ValueError, match="invalid key"):
        iss_health_check_environment.parse_encryption_keys(f"{KEY_1},not-a-key")
