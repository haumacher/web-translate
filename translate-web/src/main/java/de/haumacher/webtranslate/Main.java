package de.haumacher.webtranslate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.ParserConfigurationException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xml.sax.SAXException;

public class Main {

	public static void main(String[] args) throws IOException {
		String fileName = args[0];
		
		Document document = Jsoup.parse(new File(fileName));

		new HtmlAnalyzer(document).analyze();
		
	    String baseName = fileName.endsWith(".html") ? fileName.substring(0, fileName.length() - 4) : fileName;
		try (FileOutputStream out = new FileOutputStream(new File(baseName + "-id.html"))) {
			try (OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
				document.html(w);
			}
	    }
	}
	
}
