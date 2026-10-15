from unittest.mock import patch

from addons.images.firmware_manager.upgrade_runner import download_blob


@patch("addons.images.firmware_manager.upgrade_runner.download_blob.logging")
@patch("addons.images.firmware_manager.upgrade_runner.download_blob.shutil.copyfile")
def test_download_docker_blob(mock_copy, mock_logging):
    # prepare
    blob_name = "test.tar"
    destination_file_name = "/home/test/"

    # run
    assert download_blob.download_docker_blob(blob_name, destination_file_name)

    # validate
    mock_copy.assert_called_with(f"/mnt/blob_storage/{blob_name}", destination_file_name)
    mock_logging.info.assert_called_with(
        f"Copied storage object {blob_name} from directory /mnt/blob_storage to local file {destination_file_name}."
    )


@patch("common.util.logging")
def test_download_docker_blob_unsupported_file_type(mock_logging):
    # prepare
    blob_name = "test.blob"
    destination_file_name = "/home/test/"

    # run
    result = download_blob.download_docker_blob(blob_name, destination_file_name)

    # validate
    mock_logging.error.assert_called_with(
        f'Unsupported file type for storage object {blob_name}. Only ".tar" files are supported.'
    )
    assert result == False


@patch("addons.images.firmware_manager.upgrade_runner.download_blob.shutil.copyfile")
def test_optional_script_missing_does_not_report_success(mock_copy):
    mock_copy.side_effect = FileNotFoundError()
    assert not download_blob.download_docker_blob("vendor/model/v1/post_upgrade.sh", "/tmp/script.sh", ".sh")
    mock_copy.assert_called_once()


@patch("addons.images.firmware_manager.upgrade_runner.download_blob.shutil.copyfile")
def test_optional_script_can_still_be_copied_when_present(mock_copy):
    assert download_blob.download_docker_blob("vendor/model/v1/post_upgrade.sh", "/tmp/script.sh", ".sh")
    mock_copy.assert_called_once_with("/mnt/blob_storage/vendor/model/v1/post_upgrade.sh", "/tmp/script.sh")
