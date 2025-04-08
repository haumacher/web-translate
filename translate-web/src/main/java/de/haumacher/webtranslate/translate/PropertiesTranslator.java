package de.haumacher.webtranslate.translate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
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

	private File input;
	private File outputDir;
	private String srcLang;
	private String destLang;
	private DeepLClient client;
	
	private int  totalChars;

	public PropertiesTranslator(String apikey, String srcLang, String destLang, File input, File outputDir) {
		this.srcLang = srcLang;
		this.destLang = destLang;
		this.input = input;
		this.outputDir = outputDir;
		
        client = new DeepLClient(apikey);		
	}

	private void translate() throws IOException, DeepLException, InterruptedException {
		translate(input);
		
		System.err.println("Total billed chars: " + totalChars);
	}

	private void translate(File file) throws IOException, DeepLException, InterruptedException {
		if (file.isDirectory()) {
			for (File sub : file.listFiles()) {
				translate(sub);
			}
		} else if (file.getName().endsWith(".properties")) {
			translateProperties(file);
		}
	}

	private void translateProperties(File file) throws IOException, DeepLException, InterruptedException {
		System.err.println("Processing: " + file.getPath());
		
		Path path = input.toPath().relativize(file.toPath());
		File output = outputDir.toPath().resolve(path).toFile();

		Properties srcProperties = new Properties();
		srcProperties.load(new FileInputStream(file));
		
		Properties destProperties = new Properties();
		if (output.exists()) {
			destProperties.load(new FileInputStream(output));
		}

		List<String> keys = srcProperties.keySet().stream().map(x -> ((String) x)).sorted().toList();
		List<String> inputs = new ArrayList<>();
		StringBuilder context = new StringBuilder();
		int cnt = 0;
		for (String key : keys) {
			if (destProperties.containsKey(key)) {
				context.append(srcProperties.getProperty(key));
				context.append("\n");
			} else {
				inputs.add(srcProperties.getProperty(key));
				cnt++;
			}
		}
		
		if (cnt > 0) {
			List<TextResult> results = client.translateText(inputs, srcLang, destLang);
			
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
				new PropertiesWriter(out).write(updated);
			}
			
			System.err.println("Translated " + cnt + " messages, billed chars: " + chars);
			
			totalChars += chars;
		} else {
			System.err.println("No change.");
		}
	}

	public static void main(String[] args) throws IOException, DeepLException, InterruptedException {
		new PropertiesTranslator(args[0], args[1], args[2], new File(args[3]), new File(args[4])).translate();
	}
}
