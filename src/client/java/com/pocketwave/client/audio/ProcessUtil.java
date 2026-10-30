package com.pocketwave.client.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ProcessUtil {
	private ProcessUtil() {
	}

	static Result run(List<String> command, long timeoutSeconds) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		Process process = builder.start();

		StringBuilder output = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8), 8192)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (isProgressLine(line)) {
					continue;
				}
				if (!output.isEmpty()) {
					output.append('\n');
				}
				output.append(line);
			}
		}

		boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			throw new IOException("Command timed out: " + String.join(" ", command));
		}

		return new Result(process.exitValue(), output.toString());
	}

	private static boolean isProgressLine(String line) {
		return line.startsWith("[download]") && line.indexOf('%') >= 0;
	}

	static boolean commandAvailable(String command) {
		try {
			ProcessBuilder builder = new ProcessBuilder(command, "--version");
			builder.redirectErrorStream(true);
			Process process = builder.start();
			boolean finished = process.waitFor(10, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0 || process.exitValue() == 1;
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	record Result(int exitCode, String output) {
	}
}
