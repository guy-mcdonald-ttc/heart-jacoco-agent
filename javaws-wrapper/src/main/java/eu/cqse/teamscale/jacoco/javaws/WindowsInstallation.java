package eu.cqse.teamscale.jacoco.javaws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.conqat.lib.commons.filesystem.FileSystemUtils;

public class WindowsInstallation {

	private static final String JNLP_FTYPE = "JNLPFile";
	private static final String SECURITY_POLICY_FILE = "javaws.policy";
	private static final String FTYPE_MAPPING_BACKUP_FILE = "ftype.bak";

	private final Path systemSecurityPolicy;
	private final WrapperPaths wrapperPaths;
	private final BackupPaths backupPaths;

	public WindowsInstallation() throws InstallationException {
		this.systemSecurityPolicy = getJvmInstallPath().resolve("lib/security").resolve(SECURITY_POLICY_FILE);
		this.wrapperPaths = new WrapperPaths(getCurrentWorkingDirectory());
		this.backupPaths = new BackupPaths(getCurrentWorkingDirectory().resolve("backup"));
		validate();
	}

	private void validate() throws InstallationException {
		try {
			FileSystemUtils.mkdirs(backupPaths.backupDirectory.toFile());
		} catch (IOException e) {
			throw new InstallationException("Cannot create backup directory at " + backupPaths.backupDirectory, e);
		}
		// TODO (FS)
	}

	public boolean isInstalled() {
		return Files.exists(backupPaths.ftypeMapping);
	}

	public void install() throws InstallationException {
		try {
			Files.copy(systemSecurityPolicy, backupPaths.securityPolicy, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new InstallationException("Failed to backup current javaws security policy from "
					+ systemSecurityPolicy + " to " + backupPaths.securityPolicy, e);
		}
		try {
			FileSystemUtils.writeFileUTF8(backupPaths.ftypeMapping.toFile(), readCurrentFtype());
		} catch (IOException | InterruptedException e) {
			throw new InstallationException(
					"Failed to backup current file type mapping for JNLP files to " + backupPaths.ftypeMapping, e);
		}

		try {
			Files.copy(wrapperPaths.securityPolicy, systemSecurityPolicy, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new InstallationException("Failed to overwrite the system security policy at " + systemSecurityPolicy,
					e);
		}
		try {
			setFtype(wrapperPaths.wrapperExecutable.toAbsolutePath() + " \"%1\"");
		} catch (IOException | InterruptedException e) {
			throw new InstallationException("Failed to change the file type mapping for JNLP files", e);
		}
	}

	public void uninstall() throws InstallationException {
		String oldFtypeMapping;
		try {
			oldFtypeMapping = FileSystemUtils.readFileUTF8(backupPaths.ftypeMapping.toFile());
		} catch (IOException e) {
			throw new InstallationException("Failed to read the backup of the file type mapping for JNLP files from "
					+ backupPaths.ftypeMapping, e);
		}

		try {
			setFtype(oldFtypeMapping);
		} catch (IOException | InterruptedException e) {
			throw new InstallationException("Failed to change the file type mapping for JNLP files", e);
		}
		try {
			Files.copy(backupPaths.securityPolicy, systemSecurityPolicy, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new InstallationException("Failed to overwrite the system security policy at " + systemSecurityPolicy
					+ " with the backup from " + backupPaths.securityPolicy, e);
		}

		try {
			Files.deleteIfExists(backupPaths.ftypeMapping);
			Files.deleteIfExists(backupPaths.securityPolicy);
		} catch (IOException e) {
			System.err.println(
					"Warning: Failed to delete the backup files. The uninstallation was successful but the backup files remain at "
							+ backupPaths.backupDirectory);
			e.printStackTrace(System.err);
		}
	}

	private String readCurrentFtype() throws IOException, InterruptedException {
		return runFtype(JNLP_FTYPE);
	}

	private void setFtype(String desiredFtype) throws InstallationException, IOException, InterruptedException {
		String argument = JNLP_FTYPE + "=" + desiredFtype;
		String newFtype = runFtype(argument);
		if (!newFtype.equalsIgnoreCase(argument)) {
			throw new InstallationException("Failed to set ftype " + argument);
		}
	}

	private static String runFtype(String argument) throws IOException, InterruptedException {
		CommandLine commandLine = new CommandLine("cmd.exe");
		commandLine.addArgument("/c");
		commandLine.addArgument("ftype " + argument);

		DefaultExecutor executor = new DefaultExecutor();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
		executor.setStreamHandler(streamHandler);

		executor.execute(commandLine);
		return outputStream.toString().trim();
	}

	private static final Path getJvmInstallPath() {
		// TODO (FS) -Djava.home
		return null;
	}

	private static final Path getCurrentWorkingDirectory() {
		// TODO (FS) -Duser.dir
		return null;
	}

	private class BackupPaths {

		private final Path backupDirectory;
		private final Path securityPolicy;
		private final Path ftypeMapping;

		public BackupPaths(Path backupDirectory) {
			this.backupDirectory = backupDirectory;
			this.securityPolicy = backupDirectory.resolve(SECURITY_POLICY_FILE);
			this.ftypeMapping = backupDirectory.resolve(FTYPE_MAPPING_BACKUP_FILE);
		}

	}

	private class WrapperPaths {

		private final Path securityPolicy;
		private final Path wrapperExecutable;

		public WrapperPaths(Path wrapperDirectory) {
			securityPolicy = wrapperDirectory.resolve("agent.policy");
			wrapperExecutable = wrapperDirectory.resolve("bin/javaws");
		}

	}

	public static class InstallationException extends Exception {

		private static final long serialVersionUID = 1L;

		public InstallationException(String message, Throwable cause) {
			super(message, cause);
		}

		public InstallationException(String message) {
			super(message);
		}

	}

}
