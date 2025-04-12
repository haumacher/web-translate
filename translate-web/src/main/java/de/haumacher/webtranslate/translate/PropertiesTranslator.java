package de.haumacher.webtranslate.translate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.deepl.api.DeepLClient;
import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;

import de.haumacher.webtranslate.extract.PropertiesWriter;

public class PropertiesTranslator {

	/**
	 * Top-level properties directory.
	 * 
	 * <p>
	 * The code expects that there is a sub-directory for each language. This
	 * language directory then contains property files to translate.
	 * </p>
	 */
	private File propertiesDir;
	private String srcLang;
	private List<String> destLangs;
	private DeepLClient client;
	
	private int  totalChars;
	private File src;
	private NameStrategy nameStrategy;
	private Charset propertiesCharset;
	
	public PropertiesTranslator(String apikey, String srcLang, List<String> destLangs, File propertiesDir, File src, NameStrategy nameStrategy, Charset propertiesCharset) {
		this.srcLang = srcLang;
		this.destLangs = destLangs;
		this.nameStrategy = nameStrategy;
		this.src = src != null ? src : new File(propertiesDir, srcLang);
		this.propertiesDir = propertiesDir;
		this.propertiesCharset = propertiesCharset;
		
        client = new DeepLClient(apikey);		
	}

	public void translate() throws IOException, DeepLException, InterruptedException {
		for (String destLang : destLangs) {
			File destDir = nameStrategy.destDir(propertiesDir, destLang);
			
			System.err.println();
			System.err.println("# Translating to '" + destLang + "': " + destDir);
			System.err.println();
			translate(src, destLang, destDir);
		}
		
		System.err.println("Total billed chars: " + totalChars);
	}

	private void translate(File file, String destLang, File destDir) throws IOException, DeepLException, InterruptedException {
		if (file.isDirectory()) {
			for (File sub : file.listFiles()) {
				translate(sub, destLang, destDir);
			}
		} else if (file.getName().endsWith(".properties")) {
			translateProperties(file, destLang, destDir);
		} else {
			System.err.println("WARN: Ignoring unexpected file: " + file.getPath());
		}
	}

	private void translateProperties(File file, String destLang, File destDir) throws IOException, DeepLException, InterruptedException {
		System.err.println("Processing: " + file.getPath());
		
		Path path;
		if (src.equals(file)) {
			path = src.toPath().getParent().relativize(file.toPath());
		} else {
			path = src.toPath().relativize(file.toPath());
		}
		File output = destDir.toPath().resolve(nameStrategy.destPath(path, destLang)).toFile();

		Properties srcProperties = new Properties();
		srcProperties.load(new InputStreamReader(new FileInputStream(file), propertiesCharset));
		
		Properties destProperties = new Properties();
		if (output.exists()) {
			destProperties.load(new InputStreamReader(new FileInputStream(output), propertiesCharset));
		}

		List<String> keys = srcProperties.keySet().stream().map(x -> ((String) x)).sorted().toList();
		List<String> inputs = new ArrayList<>();
		StringBuilder context = new StringBuilder();
		for (String key : keys) {
			if (destProperties.containsKey(key)) {
				context.append(srcProperties.getProperty(key));
				context.append("\n");
			} else {
				inputs.add(srcProperties.getProperty(key));
			}
		}
		
		{
			List<TextResult> results = inputs.isEmpty() ? Collections.emptyList() : client.translateText(inputs, srcLang, destLang);
			
			output.getParentFile().mkdirs();
			
			Map<String, String> updated = new HashMap<>();
			int chars = 0;
			Iterator<TextResult> resultIt = results.iterator();
			for (String key : keys) {
				String value;
				if (destProperties.containsKey(key)) {
					value = destProperties.getProperty(key);
				} else {
					TextResult result = resultIt.next();
					value = result.getText();
					chars += result.getBilledCharacters();
				}
				
				updated.put(key, value);
			}

			try (OutputStream out = new FileOutputStream(output)) {
				new PropertiesWriter(out, propertiesCharset).write(updated);
			}
		
			if (inputs.isEmpty()) {
				// Note: The output file must be written, even if there is not a single property defined in the source file.
				System.err.println("No change.");
			} else {
				System.err.println("Translated " + inputs.size() + " messages, billed chars: " + chars);
			}
			
			totalChars += chars;
		}
	}

	public static void main(String[] args) throws IOException, DeepLException, InterruptedException {
		String apikey = args[0];
		String srcLang = args[1];
		List<String> destLangs = Arrays.stream(args[2].split(",")).map(String::strip).toList();
		File propertiesDir = new File(args[3]);
		File srcFile = args.length > 4 ? new File(args[4]) : null;
		NameStrategy nameStrategy = args.length > 5 ? NameStrategy.valueOf(args[5]) : NameStrategy.LANG_TAG_DIR;
		Charset propertiesCharset = args.length > 6 ? Charset.forName(args[6]) : StandardCharsets.ISO_8859_1;
		
		
		new PropertiesTranslator(apikey, srcLang, destLangs, propertiesDir, srcFile, nameStrategy, propertiesCharset).translate();
	}
}
