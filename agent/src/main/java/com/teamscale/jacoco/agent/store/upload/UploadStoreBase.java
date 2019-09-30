package com.teamscale.jacoco.agent.store.upload;

import com.teamscale.jacoco.agent.store.IUploader;
import com.teamscale.jacoco.agent.store.UploaderException;
import com.teamscale.jacoco.agent.util.Benchmark;
import com.teamscale.jacoco.agent.util.LoggingUtils;
import eu.cqse.teamscale.client.HttpUtils;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import org.conqat.lib.commons.filesystem.FileSystemUtils;
import org.slf4j.Logger;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Base class for uploading the coverage zip to a provided url */
public abstract class UploadStoreBase<T> implements IUploader {

	/** The logger. */
	protected final Logger logger = LoggingUtils.getLogger(this);

	/** The URL to upload to. */
	protected final HttpUrl uploadUrl;

	/** Additional files to include in the uploaded zip. */
	protected final List<Path> additionalMetaDataFiles;

	/** The API which performs the upload */
	protected final T api;

	/** Constructor. */
	public UploadStoreBase(HttpUrl uploadUrl, List<Path> additionalMetaDataFiles) {
		this.uploadUrl = uploadUrl;
		this.additionalMetaDataFiles = additionalMetaDataFiles;

		Retrofit retrofit = HttpUtils.createRetrofit(retrofitBuilder -> retrofitBuilder.baseUrl(uploadUrl));
		api = getApi(retrofit);
	}

	/** Returns the API for creating request to the http store */
	protected abstract T getApi(Retrofit retrofit);

	/** Uploads the coverage zip to the server */
	protected abstract Response<ResponseBody> uploadCoverageZip(
			byte[] zipFileBytes) throws IOException, UploaderException;

	@Override
	public void upload(File coverageFile) {
		try (Benchmark ignored = new Benchmark("Uploading report via HTTP")) {
			if (!tryUpload(coverageFile)) {
				logger.warn("Failed to upload coverage. Won't delete local file");
			}
		}
	}

	/** Performs the upload and returns <code>true</code> if successful. */
	protected boolean tryUpload(File coverageFile) {
		logger.debug("Uploading coverage to {}", uploadUrl);

		byte[] zipFileBytes;
		try {
			zipFileBytes = createZipFile(""); // TODO actually zip the file without reading all of it
		} catch (IOException e) {
			logger.error("Failed to compile coverage zip file for upload to {}", uploadUrl, e);
			return false;
		}

		try {
			Response<ResponseBody> response = uploadCoverageZip(zipFileBytes);
			if (response.isSuccessful()) {
				return true;
			}

			String errorBody = "";
			if (response.errorBody() != null) {
				errorBody = response.errorBody().string();
			}

			logger.error("Failed to upload coverage to {}. Request failed with error code {}. Error:\n{}",
					uploadUrl, response.code(), errorBody);
			return false;
		} catch (IOException e) {
			logger.error("Failed to upload coverage to {}. Probably a network problem", uploadUrl, e);
			return false;
		} catch (UploaderException e) {
			logger.error("Failed to upload coverage to {}. The configuration is probably incorrect", uploadUrl, e);
			return false;
		}
	}

	/**
	 * Creates the zip file to upload which includes the given coverage XML and all {@link #additionalMetaDataFiles}.
	 */
	private byte[] createZipFile(String xml) throws IOException {
		try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
			try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
				 OutputStreamWriter writer = new OutputStreamWriter(zipOutputStream, "UTF-8")) {
				fillZipFile(zipOutputStream, writer, xml);
			}
			return byteArrayOutputStream.toByteArray();
		}
	}

	/**
	 * Fills the upload zip file with the given coverage XML and all {@link #additionalMetaDataFiles}.
	 */
	private void fillZipFile(ZipOutputStream zipOutputStream, OutputStreamWriter writer, String xml)
			throws IOException {
		zipOutputStream.putNextEntry(new ZipEntry("coverage.xml"));
		writer.write(xml);

		// We flush the writer, but don't close it here, because closing the writer
		// would also close the zipOutputStream, making further writes impossible.
		writer.flush();

		for (Path additionalFile : additionalMetaDataFiles) {
			zipOutputStream.putNextEntry(new ZipEntry(additionalFile.getFileName().toString()));
			zipOutputStream.write(FileSystemUtils.readFileBinary(additionalFile.toFile()));
		}
	}
}
