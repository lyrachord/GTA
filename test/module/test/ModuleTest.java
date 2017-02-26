package module.test;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Layer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public class ModuleTest {
	public static void main(String[] args) throws IOException {
		Configuration c = Layer.boot().configuration();
		c.modules().stream().forEach(System.out::println);

		startModule("../IDE/mods", "com.greetings", "com.greetings.Main");
	}

	static void startModule(String modulePath, String name, String mainClass) throws IOException {
		System.out.printf("starting... %s/%s/%s\n", modulePath, name, mainClass);
		Layer layer = Layer.boot();
		Configuration c = layer.configuration();

		Path path = Paths.get(modulePath);
		ModuleFinder finder = ModuleFinder.of(path);

		c = c.resolveAndBind(ModuleFinder.ofSystem(), finder, Set.of("org.astro", "com.socket", "com.greetings"));

		System.out.println("modules");
		c.modules().stream().forEach(System.out::println);
		
		Optional<ResolvedModule> result = c.findModule(name);
		if (!result.isPresent()) return;
		ResolvedModule m = result.get();
		System.out.println(m.reference());
		m.reads().stream().forEach(System.out::println);
	}
}
