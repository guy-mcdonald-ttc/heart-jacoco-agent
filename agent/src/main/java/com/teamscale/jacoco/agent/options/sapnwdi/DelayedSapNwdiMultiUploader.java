package com.teamscale.jacoco.agent.options.sapnwdi;

import com.teamscale.client.CommitDescriptor;
import com.teamscale.jacoco.agent.upload.IUploader;
import com.teamscale.jacoco.agent.upload.DelayedMultiUploaderBase;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

/**
 * Wraps multiple {@link IUploader}s in order to delay uploads until a {@link CommitDescriptor} is asynchronously made
 * available for each application. Whenever a dump happens the coverage is uploaded to all projects for
 * which a corresponding commit has already been found. Uploads for application that have not commit at that time are skipped.
 *
 * This is safe assuming that the marker class is the central entry point for the application and therefore there
 * should not be any relevant coverage for the application as long as the marker class has not been loaded.
 */
public class DelayedSapNwdiMultiUploader extends DelayedMultiUploaderBase implements IUploader {

	private final BiFunction<CommitDescriptor, SapNwdiApplications.SapNwdiApplication, IUploader> uploaderFactory;

	/** The wrapped uploader instances. */
	private final Map<SapNwdiApplications.SapNwdiApplication, IUploader> uploaders = new HashMap<>();

	/**
	 * Visible for testing. Allows tests to control the {@link Executor} to test the asynchronous functionality of this
	 * class.
	 */
	public DelayedSapNwdiMultiUploader(
			BiFunction<CommitDescriptor, SapNwdiApplications.SapNwdiApplication, IUploader> uploaderFactory) {
		this.uploaderFactory = uploaderFactory;
		registerShutdownHook();
	}


	/** Sets the commit info detected for the application. */
	public void setCommitForApplication(CommitDescriptor commit, SapNwdiApplications.SapNwdiApplication application) {
		logger.info("Found commit for " + application.markerClass + ": " + commit);
		IUploader uploader = uploaderFactory.apply(commit, application);
		uploaders.put(application, uploader);
	}

	@Override
	protected Collection<IUploader> getWrappedUploaders() {
		return uploaders.values();
	}
}
