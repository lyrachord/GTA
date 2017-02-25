package com.gengs.gta;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

public class GTA {

	public static void main(String[] args) throws Exception {
		int length = args.length;
		GTA gta = new GTA(Paths.get("").toAbsolutePath());
		for (int i = 0; i < length; i++) {
			String target = args[i].toLowerCase();
			System.out.printf("Target %s\n", target);
			switch (target) {
			case "list":
				gta.listModules();
				break;
			case "compile":
				gta.compileAll();
				break;
			case "clean":
				gta.clean();
				break;
			case "main":
				gta.listMainClasses();
				break;
			case "run":
				if (++i < length) {
					target = args[i];
					gta.run(target);
				} else System.out.println("NO MainClass specified");
				break;
			}
		}
	}

	/**
	 * default configuration
	 * 
	 * <pre>
	 * output: mods
	 * </pre>
	 */

	private final Path root;
	private Stream<Path> modules;
	private String[] moduleNames;
	private LinkedList<String> mainClasses;

	private boolean showConfig = false;
	private Path jdk9, javac;

	private GTA(Path root) {
		this.root = root;
		jdk9 = new File(ProcessHandle.current().info().command().get()).toPath().getParent();
	}

	public void clean() throws IOException {
		deleteDirectory(root.resolve("mods"));
	}

	static class Count {
		int value;

		void increment() {
			value++;
		}
	}

	public void listMainClasses() throws IOException {
		// if (modules == null) listModules();
		// ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
		mainClasses = new LinkedList<>();
		Files.find(root.resolve("mods"), 256, (p, a) -> {
			return p.getFileName().toString().endsWith(".class");
		}).parallel().forEach(this::checkMainClass);
	}

	public void run(String target) throws Exception {
		Path java = Paths.get("D:", "java/JDK9/bin/java.exe");
		if (target.indexOf('/') == -1) {
			listMainClasses();
			for (String string : mainClasses) {
				System.out.println(string);
				if (!string.endsWith(target)) continue;
				target = string;
				break;
			}
		}
		new ProcessBuilder()//
				.command(java.toString(), "-p", "mods", "-m", target)//
				.inheritIO()//
				.start()//
				.waitFor();
	}

	private void checkMainClass(Path file) {
		String[] cmdline = { jdk9.resolve("javap.exe").toString(), file.toString() };
		try {
			Process process = Runtime.getRuntime().exec(cmdline);
			for (Scanner scanner = new Scanner(process.getInputStream()); scanner.hasNext();) {
				String line = scanner.nextLine();
				// if(line.startsWith("public class"))
				// className=//public class com.greetings.Main{
				if (!line.contains("public static void main(java.lang.String[]);")) continue;
				Path filePath = root.relativize(file);
				String string = filePath.toString();
				String className = toClassName(string);
				String moduleName = string.substring(5, string.indexOf('\\', 5));
				String item = String.format("%s/%s", moduleName, className);
				mainClasses.add(item);
				System.out.printf("FOUND MainClass: %s\n", item);
				break;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static private String toClassName(String string) {
		// mods\com.greetings\com\greetings\Main.class
		int start = string.indexOf('\\', 5) + 1;
		int end = string.lastIndexOf('.');
		return string.substring(start, end).replace('\\', '.');
	}

	public void compileAll() throws Exception {
		javac = jdk9.resolve("javac.exe");
		if (modules == null) listModules();
		Path output = root.resolve("mods");
		LinkedBlockingQueue<Module> tasks = new LinkedBlockingQueue<>();
		int length = moduleNames.length;
		if (length == 1) modules.forEach(p -> compileModule(tasks, output, p));
		else {
			// parallel stream's forEach terminal operation doesn't return until all the tasks have completed.
			modules.parallel().forEach(p -> compileModule(tasks, output, p));
			if (!tasks.isEmpty()) tasks.parallelStream().forEach(this::compileModule);
		}
	}

	private void compileModule(BlockingQueue<Module> queue, Path output, Path path) {
		String moduleName = path.getFileName().toString();
		Path moduleOutput = output.resolve(moduleName);

		StringBuilder string = new StringBuilder();
		try {
			Files.createDirectories(moduleOutput);
			string.append("-d ").append(moduleOutput).append('\n')//
					.append("-p mods\n")//
					.append("-sourcepath ").append(path).append('\n');
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(".java"))
						string.append(root.relativize(file)).append('\n');
					return FileVisitResult.CONTINUE;
				}
			});
			// is temp file has some advantage?
			// Path temp = Files.createTempFile(moduleOutput, "argfile-", ".argfile");
			Path argFile = output.resolve(moduleName + ".arg");
			String content = string.toString();
			showConfig(content);
			Files.write(argFile, content.getBytes("UTF-8"));

			// NOTE: ProcessBuilder.start different with Runtime.exec
			String arg = "@" + root.relativize(argFile).toString();
			ProcessBuilder builder = new ProcessBuilder().command(javac.toString(), arg);

			File errFile = new File(argFile.toString() + "-error");
			builder.redirectError(errFile);
			Process process = builder.start();

			// String[] cmdline = { javac.toString(), argFile };
			// Process process = Runtime.getRuntime().exec(cmdline);
			// printStream(moduleName, process.getInputStream());
			// printStream(moduleName, process.getErrorStream());
			process.waitFor();
			List<String> errors = Files.readAllLines(errFile.toPath(), Charset.forName(defaultEncoding()));
			Files.delete(errFile.toPath());

			if (errors.size() > 0) {
				for (String line : errors) {
					// requires transitive static Module.Name ;
					// but there maybe multiple lines as a require clause.
					if (line.contains("module-info.java")) {
						String require = line.substring(line.lastIndexOf(' ') + 1);
						for (String module : moduleNames)
							if (require.equals(module)) {
								queue.offer(new Module(moduleName, argFile, errFile, errors));
								return;
							}
					}
					// int index = line.indexOf("requires");
					// if (index > -1) {
					// // require= line.substring(index + 8, line.lastIndexOf(';')).trim();
					//
					// // There must be two tokens
					// // get the last token, it's module name
					// StringTokenizer tokenizer = new StringTokenizer(line, " ;");
					// String require = null;
					// while (tokenizer.hasMoreElements())
					// require = tokenizer.nextToken();
					// for (String module : moduleNames)
					// if (require.equals(module)) {
					// queue.offer(new Module(moduleName, argFile, errFile, errors));
					// return;
					// }
					// }
				}
				System.out.printf("compile module %s ERROR:\n", moduleName);
				for (String line : errors)
					System.out.printf("%s: %s\n", moduleName, line);
			} else System.out.printf("compile module %s OK\n", moduleName);
			Files.delete(argFile);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	static class Module {
		final String name;
		final Path argFile;
		final File errFile;
		final List<String> errors;

		public Module(String name, Path argFile, File errFile, List<String> errors) {
			this.name = name;
			this.argFile = argFile;
			this.errFile = errFile;
			this.errors = errors;
		}
	}

	private void compileModule(Module module) {
		System.out.printf("compile module %s...\n", module.name);
		String arg = "@" + root.relativize(module.argFile).toString();
		ProcessBuilder builder = new ProcessBuilder().command(javac.toString(), arg).inheritIO();
		try {
			Process process = builder.start();
			if (process.waitFor() == 0) System.out.printf("compile module %s OK\n", module.name);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			try {
				Files.delete(module.argFile);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void showConfig(String content) {
		if (!showConfig) return;
		System.out.println("=======");
		System.out.println(content);
		System.out.println("=======");
	}

	public static void printConsole(String moduleName, InputStream stream) {
		for (Scanner scanner = new Scanner(stream, defaultEncoding()); scanner.hasNext();)
			System.out.printf("%s: %s\n", moduleName, scanner.nextLine());
	}

	private static String defaultEncoding() {
		return System.getProperty("sun.jnu.encoding");
	}

	public void listModules() throws IOException {
		Path src = root.resolve("src");
		LinkedList<Path> modules = new LinkedList<>();
		Files.walkFileTree(Files.exists(src) ? src : root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Path moduleFile = dir.resolve("module-info.java");
				if (Files.exists(moduleFile)) {
					modules.add(dir);
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}
		});
		int size = modules.size();
		System.out.printf("modules %s:", size);
		if (size > 0) {
			moduleNames = modules.stream().map(p -> {
				String name = p.getFileName().toString();
				System.out.print(' ');
				System.out.print(name);
				return name;
			}).toArray(count -> new String[count]);
			// modules.stream().forEach(p -> {
			// System.out.printf(" %s", p.getFileName());
			// });
		} else moduleNames = new String[0];
		System.out.println();
		this.modules = modules.stream();
	}

	public static void deleteDirectory(Path path) throws IOException {
		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
