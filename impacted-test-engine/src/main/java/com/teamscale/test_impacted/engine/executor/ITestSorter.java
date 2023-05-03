package com.teamscale.test_impacted.engine.executor;

import org.junit.platform.engine.TestDescriptor;

/** Interface for implementing different ways of ordering tests. */
public interface ITestSorter {

	/**
	 * Removes any tests from the test descriptor that should not be executed and changes the execution order of the
	 * remaining tests.
	 */
	void selectAndSort(TestDescriptor testDescriptor);

}
