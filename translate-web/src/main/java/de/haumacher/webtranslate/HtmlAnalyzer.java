package de.haumacher.webtranslate;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class HtmlAnalyzer {
	
	private static final String ID_ATTR = "data-tx";

	private static final Pattern textIdPattern = Pattern.compile("t0*([1-9]\\d+)");

	private Document document;
	
	private Set<Element> textParents = new HashSet<>();
	private Map<String, Element> elementById = new HashMap<>();
	private int nextId = 1;
	private DecimalFormat idFormat = new DecimalFormat("t0000");


	public HtmlAnalyzer(Document document) {
		this.document = document;
	}
	
	public void analyze() {
		scanIds(document.getDocumentElement());
		scanText(document.getDocumentElement());
		assignIds();
		cleanIds(document.getDocumentElement());
	}
	
	private void cleanIds(Element element) {
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element sub) {
				if (!textParents.contains(child)) {
					((Element) child).removeAttribute(ID_ATTR);
				}
				cleanIds(sub);
			}
		}
	}

	private void assignIds() {
		for (Element textParent : textParents) {
			if (hasTextParent(textParent)) {
				continue;
			}
			
			String id = textParent.getAttribute(ID_ATTR);
			if (id == null || id.isBlank()) {
				id = idFormat.format(nextId ++);
				textParent.setAttribute(ID_ATTR, id);
			}
			
			elementById.put(id, textParent);
		}
	}

	private boolean hasTextParent(Element textParent) {
		for (Node parent = textParent.getParentNode(); parent != null; parent = parent.getParentNode()) {
			if (textParents.contains(parent)) {
				return true;
			}
		}
		return false;
	}

	// <p id="t0040">Some text<b><i>what</i> a great</b> nonsense.</p>

	private void scanIds(Element element) {
		String id = element.getAttribute(ID_ATTR);
		if (id != null && !id.isEmpty()) {
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
				if (isText(txt)) {
					Element textParent = (Element) child.getParentNode();
					textParents.add(textParent);
				}
			}
			else if (child instanceof Element sub) {
				scanText(sub);
			}
		}
	}

	private boolean isText(String txt) {
		if (txt == null) {
			return false;
		}
		if (txt.isBlank()) {
			return false;
		}
		
		for (int n = 0, len = txt.length(); n < len; n++) {
			char ch = txt.charAt(n);
			if (Character.isLetter(ch)) {
				return true;
			}
		}
		
		return true;
	}
}
