package de.haumacher.autotranslate.html.extract;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class TextExtractor {

	private Element _root;
	private StringBuilder _buffer = new StringBuilder();
	private int _nextId = 1;

	public TextExtractor(Element element) {
		_root = element;
	}

	public String extract() {
		extractText(_root);
		return normalizeWhitespace(_buffer.toString().trim());
	}

	private String normalizeWhitespace(String text) {
		return text.replaceAll("\\s\\s+", " ");
	}

	private void extractText(Element element) {
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Text text) {
				_buffer.append(text.getTextContent());
			}
			else if (child instanceof Element sub) {
				int id = _nextId++;

				_buffer.append("<x");
				_buffer.append(id);
				_buffer.append(">");
				extractText(sub);
				_buffer.append("</x");
				_buffer.append(id);
				_buffer.append(">");
			}
		}
	}

}
