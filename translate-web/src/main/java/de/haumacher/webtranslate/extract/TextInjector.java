package de.haumacher.webtranslate.extract;

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
	
	private List<Element> children = new ArrayList<>();
	private Set<Element> contentElements = new HashSet<>();

	private Element element;
	
	public TextInjector(Element element) {
		this.element = element;
	}

	/**
	 * Indexes the elements in the subtree of the given element.
	 * 
	 * @param element          The element to index.
	 * @param hasTextSibblings Whether direct children of the given element have
	 *                         text siblings and therefore need to be indexed.
	 */
	private void analyze(Element element, boolean hasTextSibblings) {
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element sub) {
				boolean containsText = TextExtractor.containsText(sub);
				
				if (hasTextSibblings || containsText) {
					children.add(sub);
				}
				
				analyze(sub, containsText);
			}
		}
		
		if (hasTextSibblings) {
			clear(element);
			contentElements.add(element);
		}
	}

	private void clear(Element element) {
		while (element.getLastChild() != null) {
			// Clear contents.
			element.removeChild(element.getLastChild());
		}
	}

	public void inject(String text) {
		analyze(element, true);
		clear(element);
		Document doc = element.getOwnerDocument();
		
		Matcher matcher = MARKUP_PATTERN.matcher(text);
		int pos = 0;
		List<Element> stack = new ArrayList<>();
		stack.add(element);
		while (matcher.find()) {
			int start = matcher.start();
			if (start > pos) {
				stack.get(stack.size() - 1).appendChild(doc.createTextNode(text.substring(pos, start)));
			}
			
			boolean startTag = matcher.group(1) == null;
			if (startTag) {
				int index = Integer.parseInt(matcher.group(2));
				Element child = children.get(index - 1);
				if (child != null) {
					// Never use twice.
					children.set(index - 1, null);

					Element top = stack.get(stack.size() - 1);
					if (contentElements.contains(top)) {
						top.appendChild(child);
					}
					stack.add(child);
				}
			} else {
				// Pop.
				stack.remove(stack.size() - 1);
			}
			
			pos = matcher.end();
		}
		if (text.length() > pos) {
			stack.get(stack.size() - 1).appendChild(doc.createTextNode(text.substring(pos)));
		}
	}

}
