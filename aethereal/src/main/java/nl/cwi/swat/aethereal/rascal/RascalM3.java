package nl.cwi.swat.aethereal.rascal;

import static org.rascalmpl.values.uptr.RascalValueFactory.TYPE_STORE_SUPPLIER;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

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
import io.usethesource.vallang.io.binary.stream.IValueInputStream;
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
	 * Serialize M3 model in binary form in {@code dest} Code shamlessly stolen from
	 * Rascal.
	 * 
	 * @param m3   model that will be written
	 * @param dest An /absolute/path/to/the/Destination.m3
	 * @throws IOException
	 */
	public void writeM3ModelFile(IValue m3, String dest) throws IOException {
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
		IValue m3 = null;
		if (!Files.exists(Paths.get(jar + ".m3")))
			m3 = createM3FromJarFile(jar);
		else {
			ISourceLocation loc = null;
			try {
				loc = vf.sourceLocation("file", "", jar + ".m3");
			} catch (URISyntaxException e1) {
				
			}
			try (IValueInputStream in = new IValueInputStream(URIResolverRegistry.getInstance().getInputStream(loc), vf,
					TYPE_STORE_SUPPLIER)) {
				m3 = in.read();
			} catch (IOException e) {
				
			}

		}
		ISet rel = ((ISet) ((IConstructor) m3).asWithKeywordParameters().getParameter("methodInvocation"));

		return convertISetToMultimap(rel);
	}

	/**
	 * Deserialize an m3 model file to IValue
	 * @param m3path the path of m3 model
	 * @return m3 model IValue
	 */

	public IValue deserializeM3(String m3path) {
		ISourceLocation loc = null;
		try {
			loc = vf.sourceLocation("file", "", m3path + ".m3");
		} catch (URISyntaxException e) {

			return null;
		}
		try (IValueInputStream in = new IValueInputStream(URIResolverRegistry.getInstance().getInputStream(loc), vf,
				TYPE_STORE_SUPPLIER)) {
			return in.read();
		} catch (IOException e) {

			return null;
		}
	}
	/**
	 * Extract the list of method declarations/invocations from an m3 model.
	 *
	 * @param jar An m3model
	 * @return a multimap mapping each declaration to a list of invocations
	 */
	public Multimap<String, String> extractMethodInvocations(IValue m3) {
		ISet rel = ((ISet) ((IConstructor) m3).asWithKeywordParameters().getParameter("methodInvocation"));
		return convertISetToMultimap(rel);
	}
	
	
	public List<String> extractMdethodDeclarations(IValue m3) {
		
		Multimap<String, String> dec = extractDeclarations(m3);
		return dec.keySet().stream().filter(z -> z.contains("java+method")
				|| z.contains("java+constructor") 
				|| z.contains("java+initializer")
				).collect(Collectors.toList());

	}
	
	public Multimap<String, String> extractDeclarations(IValue m3) {
		ISet rel = ((ISet) ((IConstructor) m3).asWithKeywordParameters().getParameter("declarations"));
		return convertISetToMultimap(rel);
	}
	/**
	 * Compute and m3 model from a jar file
	 * @param jar An /absolute/path/to/the/Example.jar
	 * @return an m3model
	 */
	public IValue createM3FromJarFile(String jar) {
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
