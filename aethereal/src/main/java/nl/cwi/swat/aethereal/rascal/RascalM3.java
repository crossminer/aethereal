package nl.cwi.swat.aethereal.rascal;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;

import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.env.GlobalEnvironment;
import org.rascalmpl.interpreter.env.ModuleEnvironment;
import org.rascalmpl.interpreter.load.StandardLibraryContributor;
import org.rascalmpl.library.lang.java.m3.internal.EclipseJavaCompiler;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.ValueFactoryFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.io.binary.stream.IValueOutputStream;
import io.usethesource.vallang.io.binary.stream.IValueOutputStream.CompressionRate;

public class RascalM3 {
	private IValueFactory vf = ValueFactoryFactory.getValueFactory();
	private Evaluator evaluator = createRascalEvaluator(vf);

	/**
	 * Build an M3 model for {@code jar} and serialize it in binary form in
	 * {@code dest} Code shamlessly stolen from Rascal.
	 * 
	 * @param jar  An /absolute/path/to/the/Example.jar
	 * @param dest An /absolute/path/to/the/Destination.m3
	 * @throws IOException
	 */
	public void writeM3ForJarFile(String jar, String dest) throws IOException {
		IValue m3 = createM3FromJarFile(jar);
		ISourceLocation destLoc = vf.sourceLocation(dest);

		URIResolverRegistry registry = URIResolverRegistry.getInstance();
		FileChannel channel = registry.getWriteableFileChannel(destLoc, false);
		try (IValueOutputStream writer = new IValueOutputStream(channel, vf, CompressionRate.Normal)) {
			writer.write(m3);
		}
	}

	/**
	 * Extract the list of method declarations/invocations from a JAR file.
	 *
	 * @param jar An /absolute/path/to/the/Example.jar
	 * @return a multimap mapping each declaration to a list of invocations
	 */
	public Multimap<String, String> extractMethodInvocationsFromJAR(String jar) {
		IValue m3 = createM3FromJarFile(jar);
		ISet rel = ((ISet) ((IConstructor) m3).asWithKeywordParameters().getParameter("methodInvocation"));

		return convertISetToMultimap(rel);
	}

	private IValue createM3FromJarFile(String jar) {
		EclipseJavaCompiler ejc = new EclipseJavaCompiler(vf);
		return ejc.createM3FromJarFile(vf.sourceLocation(jar), evaluator);
	}

	private Multimap<String, String> convertISetToMultimap(ISet set) {
		Multimap<String, String> map = ArrayListMultimap.create();

		set.forEach(e -> {
			ITuple t = (ITuple) e;
			ISourceLocation md = (ISourceLocation) t.get(0);
			ISourceLocation mi = (ISourceLocation) t.get(1);
			map.put(md.toString(), mi.toString());
		});

		return map;
	}

	private Evaluator createRascalEvaluator(IValueFactory vf) {
		GlobalEnvironment heap = new GlobalEnvironment();
		ModuleEnvironment module = new ModuleEnvironment("$aethereal$", heap);
		PrintWriter stderr = new PrintWriter(System.err);
		PrintWriter stdout = new PrintWriter(System.out);
		Evaluator eval = new Evaluator(vf, stderr, stdout, module, heap);

		eval.addRascalSearchPathContributor(StandardLibraryContributor.getInstance());

		eval.doImport(null, "lang::java::m3::Core");
		eval.doImport(null, "lang::java::m3::AST");
		eval.doImport(null, "lang::java::m3::TypeSymbol");

		return eval;
	}
}
