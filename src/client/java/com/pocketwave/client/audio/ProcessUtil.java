package com.pocketwave.client.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

final class ProcessUtil {
	private ProcessUtil() {
	}

	static Result run(List<String> command, long timeoutSeconds) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		Process process = builder.start();

		String output;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			output = reader.lines().collect(Collectors.joining("\n"));
		}

		boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			throw new IOException("Command timed out: " + String.join(" ", command));
		}

		return new Result(process.exitValue(), output);
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
