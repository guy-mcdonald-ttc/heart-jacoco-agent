package com.teamscale.jacoco.agent.upload.http;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.teamscale.jacoco.agent.upload.HttpZipUploaderBase;
import com.teamscale.report.jacoco.CoverageFile;

import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Uploads XMLs and metadata via HTTP multi-part form data requests.
 */
public class HttpUploader extends HttpZipUploaderBase<IHttpUploadApi> {
	/** Constructor. */
	public HttpUploader(HttpUrl uploadUrl, List<Path> additionalMetaDataFiles) {
		super(uploadUrl, additionalMetaDataFiles, IHttpUploadApi.class);
	}

	@Override
	protected Response<ResponseBody> uploadCoverageZip(File zipFile) throws IOException {
		return getApi().uploadCoverageZip(zipFile);
	}

	@Override
	public void markFileForUploadRetry(CoverageFile coverageFile) {
		// Intentionally left blank as local reupload is unsupported for this uploader.
	}

	/** {@inheritDoc} */
	@Override
	public String describe() {
		return "Uploading to " + uploadUrl;
	}

}
