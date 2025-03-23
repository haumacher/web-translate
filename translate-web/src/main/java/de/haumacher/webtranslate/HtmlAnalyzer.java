package de.haumacher.webtranslate;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class HtmlAnalyzer {
	
	private static final Pattern textIdPattern = Pattern.compile("t0*([1-9]\\d+)");

	private Document document;
	private Map<String, Element> elementById = new HashMap<>();
	private int nextId = 1;
	private DecimalFormat idFormat = new DecimalFormat("t0000");


	public HtmlAnalyzer(Document document) {
		this.document = document;
	}
	
	public void analyze() {
		scanIds(document.getDocumentElement());
		scanText(document.getDocumentElement());
	}
	
	// <p id="t0040">Some text<b><i>what</i> a great</b> nonsense.</p>

	private void scanIds(Element element) {
		String id = element.getAttribute("id");
		if (id != null && !id.isEmpty()) {
			elementById.put(id, element);
			Matcher matcher = textIdPattern.matcher(id);
			if (matcher.matches()) {
				nextId = Math.max(nextId, Integer.parseInt(matcher.group(1)) + 1);
			}
		}
		
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element sub) {
				scanIds(sub);
			}
		}
	}

	private void scanText(Element element) {
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Text text) {
				String txt = text.getTextContent();
				if (txt != null && !txt.isBlank()) {
					Element textParent = (Element) child.getParentNode();
					String id = textParent.getAttribute("id");
					if (id == null || !id.isBlank()) {
						textParent.setAttribute("id", idFormat.format(nextId ++));
					}
				}
			}
			else if (child instanceof Element sub) {
				scanText(sub);
			}
		}
	}
}
