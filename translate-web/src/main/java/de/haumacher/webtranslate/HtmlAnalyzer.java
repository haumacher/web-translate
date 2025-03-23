package de.haumacher.webtranslate;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

public class HtmlAnalyzer {
	
	private static final Pattern textIdPattern = Pattern.compile("t0*([1-9]\\d+)");

	private Document document;
	private Map<String, Element> elementById = new HashMap<>();
	private int nextId = 1;
	private DecimalFormat idFormat = new DecimalFormat("t0000");


	public HtmlAnalyzer(org.jsoup.nodes.Document document2) {
		this.document = document2;
	}
	
	public void analyze() {
		scanIds(document.root());
		scanText(document.root());
	}
	
	// <p id="t0040">Some text<b><i>what</i> a great</b> nonsense.</p>

	private void scanIds(Element element) {
		String id = element.attr("id");
		if (id != null && !id.isEmpty()) {
			elementById.put(id, element);
			Matcher matcher = textIdPattern.matcher(id);
			if (matcher.matches()) {
				nextId = Math.max(nextId, Integer.parseInt(matcher.group(1)) + 1);
			}
		}
		
		for (Element child : element.children()) {
			scanIds(child);
		}
	}

	private void scanText(Element element) {
		for (Node child : element.childNodes()) {
			if (child instanceof TextNode text) {
				String txt = text.text();
				if (txt != null && !txt.isBlank()) {
					Element textParent = (Element) child.parent();
					String id = textParent.attr("id");
					if (id == null || !id.isBlank()) {
						textParent.attr("id", idFormat.format(nextId ++));
					}
				}
			}
			else if (child instanceof Element sub) {
				scanText(sub);
			}
		}
	}
}
