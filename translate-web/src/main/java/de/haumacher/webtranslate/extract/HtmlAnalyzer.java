package de.haumacher.webtranslate.extract;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * To automatically translate a Thymeleaf web page template, elements containing
 * literal text must be identified. The contained text must be extracted to a
 * properties file indexed by the element ID that contained the text. After
 * translation, a new structurally identical template must be created containing
 * the translated text. Things get tricky, if the text contains interleaving
 * markup such as:
 * 
 * <xmp>
 * <p data-tx="t0001">
 * Some text <a th:href="@{/some-url}">with markup</a>.
 * <p>
 * </xmp>
 * 
 * Such markup should not be extracted and should not be part of the
 * translation. In the above example, the extracted properties file should look
 * like as follows:
 * 
 * <xmp>t0001=Some text <x1>with markup</x1>.</xmp>
 * 
 * The translation service should produce a translation (e.g. for German) in the
 * following form:
 * 
 * <xmp> t0001=Etwas Text <x1>mit Markup</x1>. </xmp>
 * 
 * <p>
 * This can now be transformed back to a translated template by identifying the
 * nth internal tag with the tag <code>xn</code> in the translated text.
 * </p>
 * 
 * <p>
 * In the example above, the internal tag (<code>a</code>) directly contained
 * some text. Things get even more complicated, if there is a deeply nested
 * structure of sub-tags, only some of them containing text.
 * </p>
 * 
 * <xmp>
 * <p data-tx="t0002">
 * An example <b>with <th:block th:if="..."><i>considerable</i></th:block>
 * nesting</b>.
 * </p>
 * </xmp>
 * 
 * When text contains markup with deeply nested structure as in the example
 * above, each tag that has text siblings and each tag that directly contains
 * text is mapped to an identifying tag in the text to translate:
 * 
 * <xmp> t0002=An example <x1>with <x2>considerable</x2> nesting</x1>. </xmp>
 * 
 * Here, the original tag <code>b</code> is represented by <code>x1</code> and
 * <code>i</code> is represented by <code>x2</code>, while the tag
 * <code>th:block</code> has no representation in the text to translate, since
 * it neither directly contains text nor is it adjacent to translated text. This
 * approach considerably reduces structure in translated text, reducing size of
 * translation input and output and reduces potential errors during translation.
 */
public class HtmlAnalyzer {

	private static final String ID_ATTR = "data-tx";

	private static final Pattern TEXT_ID_PATTERN = Pattern.compile("t0*([1-9]\\d*)");

	private static final Set<String> CODE_TAGS = new HashSet<>(Arrays.asList("code", "pre", "script", "xmp", "style"));

	private static final Set<String> TEXT_ATTRS = new HashSet<>(Arrays.asList("alt", "label", "placeholder", "summary", "title"));
	
	private Document document;

	private Set<Element> textParents = new LinkedHashSet<>();
	private Map<String, Element> elementById = new HashMap<>();
	private int nextId = 1;
	private DecimalFormat idFormat = new DecimalFormat("t0000");
	
	private Map<String, String> textById = new HashMap<>();

	public HtmlAnalyzer(Document document) {
		this.document = document;
	}

	public void analyze() {
		scanExistingIds(document.getDocumentElement());
		scanText(document.getDocumentElement());
		assignIds();
		cleanIds(document.getDocumentElement());
		extractText(document.getDocumentElement());
	}
	
	public Map<String, String> getTextById() {
		return textById;
	}
	
	public void setTextById(Map<String, String> textById) {
		this.textById = textById;
	}

	public void inject() {
		injectText(document.getDocumentElement());
	}
	
	private void injectText(Element element) {
		String id = element.getAttribute(ID_ATTR);
		if (id != null && !id.isEmpty()) {
			NamedNodeMap attributes = element.getAttributes();
			for (int n = 0, cnt = attributes.getLength(); n < cnt; n ++) {
				Node attr = attributes.item(n);
				if (TEXT_ATTRS.contains(attr.getNodeName())) {
					String text = textById.get(id + "." + attr.getNodeName());
					if (text != null) {
						attr.setNodeValue(text);
					}
				}
			}
			
			if (!CODE_TAGS.contains(element.getTagName())) {
				String text = textById.get(id);
				if (text != null) {
					new TextInjector(element).inject(text);
				}
			}
		} else {
			for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
				if (child instanceof Element sub) {
					injectText(sub);
				}
			}
		}
	}

	private void extractText(Element element) {
		String id = element.getAttribute(ID_ATTR);
		if (id != null && !id.isEmpty()) {
			NamedNodeMap attributes = element.getAttributes();
			for (int n = 0, cnt = attributes.getLength(); n < cnt; n ++) {
				Node attr = attributes.item(n);
				if (TEXT_ATTRS.contains(attr.getNodeName())) {
					String attrText = attr.getTextContent();
					if (!attrText.isBlank()) {
						textById.put(id + "." + attr.getNodeName(), attrText);
					}
				}
			}
			
			if (!CODE_TAGS.contains(element.getTagName())) {
				// Note: The element could have an ID assigned, because it only contains text attributes. 
				if (containsText(element)) {
					textById.put(id, new TextExtractor(element).extract());
				}
			}
		} else {
			for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
				if (child instanceof Element sub) {
					extractText(sub);
				}
			}
		}
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
			if (!hasTextAttribute(textParent) && hasTextParent(textParent)) {
				textParent.removeAttribute(ID_ATTR);
				continue;
			}
			
			String id = textParent.getAttribute(ID_ATTR);
			if (id != null && !id.isBlank()) {
				if (elementById.containsKey(id)) {
					// Duplicate assignment, remove.
					textParent.removeAttribute(ID_ATTR);
					id = null;
				}
			}
			if (id == null || id.isBlank()) {
				id = idFormat.format(nextId++);
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

	/**
	 * Scans all existing text node IDs in the given document and computes the next free ID to assign.
	 */
	private void scanExistingIds(Element element) {
		String id = element.getAttribute(ID_ATTR);
		if (id != null && !id.isEmpty()) {
			Matcher matcher = TEXT_ID_PATTERN.matcher(id);
			if (matcher.matches()) {
				nextId = Math.max(nextId, Integer.parseInt(matcher.group(1)) + 1);
			}
		}

		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element sub) {
				scanExistingIds(sub);
			}
		}
	}

	/**
	 * Scans the given document for elements that contain text (either in user-facing text attributes, or text content).
	 */
	private void scanText(Element element) {
		if (hasTextAttribute(element)) {
			textParents.add(element);
		}
		
		if (CODE_TAGS.contains(element.getTagName())) {
			// No translation here.
			return;
		}
		
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Text text) {
				if (hasText(text)) {
					Element textParent = (Element) child.getParentNode();
					textParents.add(textParent);
				}
			} else if (child instanceof Element sub) {
				scanText(sub);
			}
		}
	}

	private boolean containsText(Element element) {
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Text text) {
				if (hasText(text)) {
					return true;
				}
			} else if (child instanceof Element sub) {
				if (containsText(sub)) {
					return true;
				}
			}
		}
		return false;
	}
	
	private boolean hasTextAttribute(Element element) {
		NamedNodeMap attributes = element.getAttributes();
		for (int n = 0, cnt = attributes.getLength(); n < cnt; n ++) {
			Node attr = attributes.item(n);
			if (TEXT_ATTRS.contains(attr.getNodeName())) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasText(Text text) {
		String s = text.getTextContent();

		if (s == null) {
			return false;
		}
		if (s.isBlank()) {
			return false;
		}

		for (int n = 0, len = s.length(); n < len; n++) {
			char ch = s.charAt(n);
			if (Character.isLetter(ch)) {
				return true;
			}
		}

		return true;
	}
}
