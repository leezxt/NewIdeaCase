package com.newideacase.platform.file.application;

import java.net.URI;
import java.time.Duration;

public interface ObjectStoragePort {

    URI createUploadUrl(String objectKey, String contentType, long size, Duration validity);

    URI createDownloadUrl(String objectKey, Duration validity);

    void delete(String objectKey);
}
