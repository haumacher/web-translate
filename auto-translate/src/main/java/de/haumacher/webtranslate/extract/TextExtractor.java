package de.haumacher.webtranslate.extract;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class TextExtractor {

	private Element root;
	private StringBuilder buffer = new StringBuilder();
	private int nextId = 1;

	public TextExtractor(Element element) {
		this.root = element;
	}

	public String extract() {
		extractText(root, true);
		return normalizeWhitespace(buffer.toString().trim());
	}

	private String normalizeWhitespace(String text) {
		return text.replaceAll("\\s\\s+", " ");
	}

	private void extractText(Element element, boolean hasTextSibbling) {
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Text text) {
				buffer.append(text.getTextContent());
			}
			else if (child instanceof Element sub) {
				boolean subText = containsText(sub);
				if (hasTextSibbling || subText) {
					int id = nextId++;
					
					buffer.append("<x");
					buffer.append(id);
					buffer.append(">");
					extractText(sub, subText);
					buffer.append("</x");
					buffer.append(id);
					buffer.append(">");
				} else {
					extractText(sub, false);
				}
			}
		}
	}

	public static boolean containsText(Element element) {
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Text text) {
				if (HtmlAnalyzer.hasText(text)) {
					return true;
				}
			}
		}
		return false;
	}

}
