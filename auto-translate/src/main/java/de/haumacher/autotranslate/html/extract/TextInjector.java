package de.haumacher.autotranslate.html.extract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class TextInjector {

	private static final Pattern MARKUP_PATTERN = Pattern.compile("<(/)?x([1-9]\\d*)>");

	private List<Element> _children = new ArrayList<>();
	private Set<Element> _contentElements = new HashSet<>();

	private Element _element;

	public TextInjector(Element element) {
		_element = element;
	}

	/**
	 * Indexes the elements in the subtree of the given element.
	 * 
	 * @param element          The element to index.
	 * @param hasTextSibblings Whether direct children of the given element have
	 *                         text siblings and therefore need to be indexed.
	 */
	private void analyze(Element element) {
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element sub) {
				_children.add(sub);
				analyze(sub);
			}
		}

		clear(element);
		_contentElements.add(element);
	}

	private void clear(Element element) {
		while (element.getLastChild() != null) {
			// Clear contents.
			element.removeChild(element.getLastChild());
		}
	}

	public void inject(String text) {
		try {
			doInject(text);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Failed to inject: " + text, ex);
		}
	}
	
	private void doInject(String text) {
		analyze(_element);
		clear(_element);
		Document doc = _element.getOwnerDocument();

		Matcher matcher = MARKUP_PATTERN.matcher(text);
		int pos = 0;
		Stack<Element> elements = new Stack<>();
		Stack<Integer> ids = new Stack<>();
		elements.push(_element);
		while (matcher.find()) {
			int start = matcher.start();
			if (start > pos) {
				elements.top().appendChild(doc.createTextNode(text.substring(pos, start)));
			}

			boolean startTag = matcher.group(1) == null;
			int id = Integer.parseInt(matcher.group(2));

			if (startTag) {
				int index = id - 1;
				Element child = _children.get(index);
				if (child != null) {
					Element top = elements.top();
					while (!_contentElements.contains(top)) {
						// Pop.
						elements.pop();
						ids.pop();
						
						top = elements.top();
					}
					
					// Never use twice.
					_children.set(index, null);
					top.appendChild(child);

					// Push
					ids.push(id);
					elements.push(child);
				}
			} else {
				if (ids.hasTop() && ids.top().equals(id)) {
					// Pop.
					elements.pop();
					ids.pop();
				} else if (ids.contains(id)) {
					// Missing end tags, pop them all.
					while (!ids.top().equals(id)) {
						System.err.println("WARN: Missing end tag for start tag '<x" + ids.top() + ">' in: " + text);
						elements.pop();
						ids.pop();
					}
					elements.pop();
					ids.pop();
				} else {
					System.err.println("WARN: Missing start tag for end tag '" + matcher.group() + "' in: " + text);
				}
			}

			pos = matcher.end();
		}
		if (text.length() > pos) {
			elements.top().appendChild(doc.createTextNode(text.substring(pos)));
		}
		
		// Safety: Do not loose contents: Add unused element to root.
		Element top = elements.pop();
		while (!elements.isEmpty()) {
			top = elements.pop();
		}
		for (Element leftOver : _children) {
			if (leftOver != null) {
				top.appendChild(leftOver);
			}
		}
	}

}
