package edu.usfca.cs272;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.listeners.TestExecutionSummary.Failure;

/**
 * Utility methods used by other JUnit test classes.
 *
 * @author CS 272 Software Development (University of San Francisco)
 * @version Fall 2022
 */
public class ProjectTests {
	/**
	 * Detect whether paths are Unix-like (true for Linux, Mac, but not Windows)
	 */
	public static final boolean NIX = File.separator.equals("/");

	/** Path to the actual output files. */
	public static final Path ACTUAL_PATH = Path.of("actual");

	/** Path to the expected output files (based on type of slash). */
	public static final Path EXPECTED_PATH = Path.of(NIX ? "expected-nix" : "expected-win");

	/** Path to the input files. */
	public static final Path INPUT_PATH = Path.of("input");

	/** Path to the input text files. */
	public static final Path TEXT_PATH = INPUT_PATH.resolve("text");

	/** Path to the input query files. */
	public static final Path QUERY_PATH = INPUT_PATH.resolve("query");

	/** Flag to indicate the path to index as text. */
	public static final String TEXT_FLAG = "-text";

	/** Flag to indicate the URL to index as html. */
	public static final String HTML_FLAG = "-html";

	/** Flag to indicate what queries to search for in the index. */
	public static final String QUERY_FLAG = "-query";

	/** Flag to indicate whether to conduct an exact or partial search. */
	public static final String EXACT_FLAG = "-exact";

	/** Flag to enable threading with the specified number of threads. */
	public static final String THREADS_FLAG = "-threads";

	/** The default value for the threads flag. */
	public static final int THREADS_DEFAULT = 5;

	/** Flag to set the maximum number of URLs to process as HTML. */
	public static final String MAX_FLAG = "-max";

	/** Flag to enable the web server on the specified port. */
	public static final String SERVER_FLAG = "-server";

	/** The default value for the server or port flag. */
	public static final int PORT_DEFAULT = 8080;

	/** Flag to indicate whether to output the inverted index to JSON. */
	public static final String INDEX_FLAG = "-index";

	/** The default value for the index output path. */
	public static final Path INDEX_DEFAULT = Path.of("index.json");

	/** Flag to indicate whether to output the location word counts to JSON. */
	public static final String COUNTS_FLAG = "-counts";

	/** The default value for the result output path flag. */
	public static final Path COUNTS_DEFAULT = Path.of("counts.json");

	/** Flag to indicate whether to output the search results to JSON. */
	public static final String RESULTS_FLAG = "-results";

	/** The default value for the result output path flag. */
	public static final Path RESULTS_DEFAULT = Path.of("results.json");

	/** Format string for JUnit error output. */
	private static final String ERROR_FORMAT = """

			Working Directory:
			%s

			Actual File:
			%s

			Expected File:
			%s

			Arguments:
			%s

			Message:
			%s
			""";

	/** Amount of time to wait for long-running tests to finish. */
	public static final Duration LONG_TIMEOUT = Duration.ofMinutes(5);

	/** Amount of time to wait for short-running tests to finish. */
	public static final Duration SHORT_TIMEOUT = Duration.ofSeconds(30);

	/**
	 * Produces debug-friendly output when a JUnit test fails.
	 *
	 * @param args original arguments sent to {@link Driver}
	 * @param actual path to actual output
	 * @param expected path to expected output
	 * @param message error message with more details
	 *
	 * @return formatted error message
	 */
	public static String errorMessage(String[] args, Path actual, Path expected, String message) {
		Path working = Path.of(".").toAbsolutePath().normalize();
		return String.format(ERROR_FORMAT, working.toString(), actual.toString(), expected.toString(),
				String.join(" ", args), message);
	}

	/**
	 * Checks line-by-line if two files are equal. If one file contains extra
	 * blank lines at the end of the file, the two are still considered equal.
	 * Works even if the path separators in each file are different.
	 *
	 * @param path1 path to first file to compare with
	 * @param path2 path to second file to compare with
	 * @return positive value if two files are equal, negative value if not
	 *
	 * @throws IOException if IO error occurs
	 */
	public static int checkFiles(Path path1, Path path2) throws IOException {
		// used to output line mismatch
		int count = 0;

		try (
				BufferedReader reader1 = Files.newBufferedReader(path1, UTF_8);
				BufferedReader reader2 = Files.newBufferedReader(path2, UTF_8);
		) {
			String line1 = reader1.readLine();
			String line2 = reader2.readLine();

			while (true) {
				count++;

				// compare lines until we hit a null (i.e. end of file)
				if (line1 != null && line2 != null) {
					// remove trailing spaces
					line1 = line1.stripTrailing();
					line2 = line2.stripTrailing();

					// check if lines are equal
					if (!line1.equals(line2)) {
						return -count;
					}

					// read next lines if we get this far
					line1 = reader1.readLine();
					line2 = reader2.readLine();
				}
				else {
					// discard extra blank lines at end of reader1
					while (line1 != null && line1.isBlank()) {
						line1 = reader1.readLine();
					}

					// discard extra blank lines at end of reader2
					while (line2 != null && line2.isBlank()) {
						line2 = reader2.readLine();
					}

					if (line1 == line2) {
						// only true if both are null, otherwise one file had extra non-empty lines
						return count;
					}
					else {
						// extra blank lines found in one file
						return -count;
					}
				}
			}
		}
	}

	/**
	 * Checks whether {@link Driver} generates the expected output without any
	 * exceptions. Will print the stack trace if an exception occurs. Designed to
	 * be used within an unit test. If the test was successful, deletes the actual
	 * file. Otherwise, keeps the file for debugging purposes.
	 *
	 * @param args arguments to pass to {@link Driver}
	 * @param actual path to actual output
	 * @param expected path to expected output
	 */
	public static void checkOutput(String[] args, Path actual, Path expected) {
		try {
			// Remove old actual file (if exists), setup directories if needed
			Files.deleteIfExists(actual);
			Files.createDirectories(actual.getParent());

			// Generate actual output file
			System.out.printf("%nRunning: %s...%n", actual.toString());
			Driver.main(args);

			// Double-check we can read the expected output file
			if (!Files.isReadable(expected)) {
				String message = "Unable to read expected output file.";
				Assertions.fail(errorMessage(args, actual, expected, message));
			}

			// Double-check we can read the actual output file
			if (!Files.isReadable(actual)) {
				String message = "Unable to read actual output file.";
				Assertions.fail(errorMessage(args, actual, expected, message));
			}

			// Compare the two files
			int count = checkFiles(actual, expected);

			if (count <= 0) {
				String message = "Difference detected on line: " + -count + ".";
				Assertions.fail(errorMessage(args, actual, expected, message));
			}

			// At this stage, the files were the same and we can delete actual.
			Files.deleteIfExists(actual);
		}
		catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));

			String message = writer.toString();
			Assertions.fail(errorMessage(args, actual, expected, message));
		}
	}

	/**
	 * Checks whether {@link Driver} will run without generating any exceptions.
	 * Will print the stack trace if an exception occurs. Designed to be used
	 * within an unit test.
	 *
	 * @param args arguments to pass to {@link Driver}
	 * @param timeout the duration to run before timing out
	 */
	public static void testNoExceptions(String[] args, Duration timeout) {
		Assertions.assertTimeoutPreemptively(timeout, () -> {
			try {
				System.out.printf("%nRunning Driver %s...%n", String.join(" ", args));
				Driver.main(args);
			}
			catch (Exception e) {
				StringWriter writer = new StringWriter();
				e.printStackTrace(new PrintWriter(writer));

				String debug = String.format("%nArguments:%n    [%s]%nException:%n    %s%n",
						String.join(" ", args), writer.toString());
				Assertions.fail(debug);
			}
		});
	}

	/**
	 * Attempts to test that multiple threads are being used in this code.
	 *
	 * @param action the action to run
	 */
	public static void testMultithreaded(Runnable action) {
		// time the action run
		Instant start = Instant.now();
		action.run();
		Duration elapsed = Duration.between(start, Instant.now());

		// get how long to pause when checking for multithreading
		Duration pause = elapsed.dividedBy(3);

		Assertions.assertTimeoutPreemptively(LONG_TIMEOUT, () -> {
			// get the non-worker threads that are running this test code
			List<String> before = activeThreads();

			// start up the Driver thread
			Thread driver = new Thread(action);
			driver.setPriority(Thread.MAX_PRIORITY);
			driver.start();

			// pause this thread for a bit (this is where things can go wrong)
			// this gives Driver a chance to start up its worker threads
			Thread.sleep(pause.toMillis());

			// get the threads (ideally Driver should be up and running by this point)
			String error = "Something went wrong with the test code; see instructor. Elapsed: %d, Pause: %d";
			Assertions.assertTrue(driver.isAlive(), error.formatted(elapsed.toMillis(), pause.toMillis()));
			List<String> finish = activeThreads();

			// wait for Driver to finish up
			driver.join();

			// try to figure out which ones are worker threads
			List<String> workers = new ArrayList<>(finish);
			workers.removeAll(before); // remove threads running this code
			workers.remove(driver.getName()); // remove the driver thread
			workers.removeIf(name -> name.startsWith("junit")); // remove junit timeout threads
			workers.removeIf(name -> name.startsWith("ForkJoinPool")); // remove other junit threads

			String debug = "\nThreads Before: %s\nThreads After: %s\nWorker Threads: %s\n";
			Assertions.assertTrue(workers.size() > 0, debug.formatted(before, finish, workers));
		});
	}

	/**
	 * Returns a list of the active thread names (approximate).
	 *
	 * @return list of active thread names
	 */
	public static List<String> activeThreads() {
		int active = Thread.activeCount(); // only an estimate
		Thread[] threads = new Thread[active * 2]; // make sure large enough
		Thread.enumerate(threads);

		return Arrays.stream(threads).filter(t -> t != null).map(t -> t.getName()).collect(Collectors.toList());
	}

	/**
	 * Generates the output file name to use given the prefix and path.
	 *
	 * @param prefix the prefix to use, like "index" or "results"
	 * @param path the input path being tested
	 * @return the output file name to use
	 */
	public static String outputFileName(String prefix, Path path) {
		// determine filename to use for actual/expected output
		if (Files.isDirectory(path)) {
			// e.g. index-simple.json
			return String.format("%s-%s.json", prefix, path.getFileName().toString());
		}
		else {
			// e.g. index-simple-hello.json
			String[] parts = path.getFileName().toString().split("\\.");
			String subdir = path.getParent().getFileName().toString();
			return String.format("%s-%s-%s.json", prefix, subdir, parts[0]);
		}
	}

	/**
	 * Generates the output file name to use given the prefix, path, and number of
	 * threads.
	 *
	 * @param prefix the prefix to use, like "index" or "results"
	 * @param path the input path being tested
	 * @param threads the number of threads
	 * @return the output file name to use
	 */
	public static String outputFileName(String prefix, Path path, int threads) {
		// determine filename to use for actual/expected output
		if (Files.isDirectory(path)) {
			// e.g. index-simple-1.json
			return String.format("%s-%s-%d.json", prefix, path.getFileName().toString(), threads);
		}
		else {
			// e.g. index-simple-hello-1.json
			String[] parts = path.getFileName().toString().split("\\.");
			String subdir = path.getParent().getFileName().toString();
			return String.format("%s-%s-%s-%d.json", prefix, subdir, parts[0], threads);
		}
	}

	/**
	 * Makes sure the expected environment is setup before running any tests.
	 */
	@BeforeAll
	public static void testEnvironment() {
		System.out.println("Using: " + EXPECTED_PATH);

		try {
			Files.createDirectories(ACTUAL_PATH);

			// delete any old files located in actual directory
			System.out.println("Cleaning up old actual files...");
			Files.walk(ACTUAL_PATH).filter(Files::isRegularFile).forEach(path -> {
				try {
					Files.delete(path);
				}
				catch (IOException e) {
					System.out.println("Warning: Unable to delete actual file " + path.toString());
				}
			});
		}
		catch (IOException e) {
			Assertions.fail("Unable to create actual output directory.");
		}

		try {
			// only make Windows files if necessary
			if (!NIX) {
				System.out.println("Windows detected; generating expected files.");
				List<Path> copied = copyExpected();
				System.out.println("Copied: " + copied.size() + " files.");
				copied.forEach(System.out::println);
				System.out.println();
			}
		}
		catch (IOException e) {
			Assertions.fail("Unable to copy expected files for Windows systems.");
		}

		Assertions.assertAll(() -> Assertions.assertTrue(Files.isReadable(EXPECTED_PATH), EXPECTED_PATH.toString()),
				() -> Assertions.assertTrue(Files.isWritable(ACTUAL_PATH), ACTUAL_PATH.toString()),
				() -> Assertions.assertTrue(Files.isDirectory(TEXT_PATH), TEXT_PATH.toString()),
				() -> Assertions.assertTrue(Files.isDirectory(QUERY_PATH), QUERY_PATH.toString()));
	}

	/**
	 * Copies the expected files for Unix-like operating systems to expected files
	 * for Windows operating systems.
	 *
	 * @return all of the files copied
	 * @throws IOException if an IO error occurs
	 */
	public static List<Path> copyExpected() throws IOException {
		List<Path> copied = new ArrayList<>();
		Path nix = Path.of("expected-nix");
		Path win = Path.of("expected-win");

		// loop through each expected file
		try (Stream<Path> stream = Files.walk(nix, FileVisitOption.FOLLOW_LINKS)) {
			for (Path path : stream.collect(Collectors.toList())) {
				// path for windows version of expected file
				Path other = win.resolve(nix.relativize(path));

				// check if we need to re-copy the expected file
				if (Files.isRegularFile(path)) {
					if (!Files.isReadable(other) || Files.size(path) != Files.size(other)) {
						String original = Files.readString(path, StandardCharsets.UTF_8);

						if (path.startsWith(nix.resolve("crawl"))) {
							Files.writeString(other, original, StandardCharsets.UTF_8);
						}
						else {
							String modified = original.replace('/', '\\');
							Files.writeString(other, modified, StandardCharsets.UTF_8);
						}
						copied.add(other);
					}
				}
				else if (Files.isDirectory(path) && !Files.exists(other)) {
					Files.createDirectories(other);
				}
			}
		}

		return copied;
	}

	/**
	 * Tries to extract a human-readable test name from the failure.
	 *
	 * @param failure the test failure
	 * @return readable test name of the failure
	 */
	public static String parseTestId(Failure failure) {
		// [engine:junit-jupiter]/[class:Project3aTest]/[nested-class:A_ThreadBuildTest]/[test-template:testText(int)]/[test-template-invocation:#1]
		TestIdentifier test = failure.getTestIdentifier();
		String id = test.getUniqueId();

		String regex = ".+?\\[nested-class:(.+?)\\].+?\\[(?:test-template|method):(.+?)\\](?:.*?\\[test-template-invocation:(.+?)\\])?";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(id);

		if (matcher.matches()) {
			String className = matcher.group(1);
			String methodName = matcher.group(2);
			String repeatName = matcher.group(3);

			repeatName = repeatName == null ? "" : repeatName;
			return className + "." + methodName + repeatName;
		}

		return id;
	}

	/**
	 * Encourages the garbage collector to run; useful in between intensive groups
	 * of tests or before benchmarking.
	 */
	public static void freeMemory() {
		Runtime runtime = Runtime.getRuntime();
		long bytes = 1048576;
		double before = (double) (runtime.totalMemory() - runtime.freeMemory()) / bytes;

		// try to free up memory before another run of intensive tests
		runtime.gc();

		// collect rest of system information
		int processors = runtime.availableProcessors();
		double maximum = (double) runtime.maxMemory() / bytes;
		double after = (double) (runtime.totalMemory() - runtime.freeMemory()) / bytes;

		String format = """

				%8.2f Processors
				%8.2f MB Memory Maximum
				%8.2f MB Memory Used (Before GC)
				%8.2f MB Memory Used (After GC)

				""";

		System.out.printf(format, (double) processors, maximum, before, after);
	}

	/**
	 * Runs the SearchEngine Driver class by default.
	 *
	 * @param args the arguments to pass to Driver
	 */
	public static void main(String[] args) {
		Driver.main(args);
	}
}
