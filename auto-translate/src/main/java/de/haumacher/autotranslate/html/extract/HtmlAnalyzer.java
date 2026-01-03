package de.haumacher.autotranslate.html.extract;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

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
 * <pre>{@code
 * <p data-tx="t0001">
 * Some text <a th:href="@{/some-url}">with markup</a>.
 * <p>
 * }</pre>
 *
 * Such markup should not be extracted and should not be part of the
 * translation. In the above example, the extracted properties file should look
 * like as follows:
 *
 * <pre>{@code t0001=Some text <x1>with markup</x1>.}</pre>
 *
 * The translation service should produce a translation (e.g. for German) in the
 * following form:
 *
 * <pre>{@code t0001=Etwas Text <x1>mit Markup</x1>. }</pre>
 *
 * <p>
 * This can now be transformed back to a translated template by identifying the
 * nth internal tag with the tag {@code <xn>} in the translated text.
 * </p>
 *
 * <p>
 * In the example above, the internal tag ({@code <a>}) directly contained
 * some text. Things get even more complicated, if there is a deeply nested
 * structure of sub-tags, only some of them containing text.
 * </p>
 *
 * <pre>{@code
 * <p data-tx="t0002">
 * An example <b>with <th:block th:if="..."><i>considerable</i></th:block>
 * nesting</b>.
 * </p>
 * }</pre>
 *
 * When text contains markup with deeply nested structure as in the example
 * above, each tag that has text siblings and each tag that directly contains
 * text is mapped to an identifying tag in the text to translate:
 *
 * <pre>{@code t0002=An example <x1>with <x2>considerable</x2> nesting</x1>. }</pre>
 *
 * Here, the original tag {@code <b>} is represented by {@code <x1>} and
 * {@code <i>} is represented by {@code <x2>}, while the tag
 * {@code <th:block>} has no representation in the text to translate, since
 * it neither directly contains text nor is it adjacent to translated text. This
 * approach considerably reduces structure in translated text, reducing size of
 * translation input and output and reduces potential errors during translation.
 */
public class HtmlAnalyzer {

	private static final String ID_ATTR = "data-tx";

	/**
	 * Pattern to match data-tx attribute values with optional CRC: "t0001" or "t0001:abc123"
	 */
	private static final Pattern TEXT_ID_PATTERN = Pattern.compile("t0*([1-9]\\d*)(?::([0-9a-f]+))?");

	private static final Set<String> CODE_TAGS = new HashSet<>(Arrays.asList("code", "pre", "script", "xmp", "style"));

	private static final Set<String> TEXT_ATTRS = new HashSet<>(Arrays.asList("alt", "label", "placeholder", "summary", "title", "aria-label"));

	private Document _document;

	/**
	 * Mapping of text element (that either has a text attribute or text content to a decision, whether the element contains text content).
	 */
	private Map<Element, Boolean> _textParents = new LinkedHashMap<>();
	private Map<String, Element> _elementById = new HashMap<>();
	private int _nextId = 1;
	private DecimalFormat _idFormat = new DecimalFormat("t0000");

	private Map<String, String> _textById = new HashMap<>();

	/**
	 * Map from text ID to CRC checksum of the text.
	 * Used to detect when text has changed and needs re-translation.
	 */
	private Map<String, String> _crcById = new HashMap<>();

	/**
	 * Map from text ID to old CRC checksum that was in the source document.
	 * Used to detect which texts have changed.
	 */
	private Map<String, String> _oldCrcById = new HashMap<>();

	/**
	 * Set of text IDs that existed in the source document before analysis.
	 * These IDs are considered "existing" and don't need unconditional translation.
	 */
	private Set<String> _existingIds = new HashSet<>();

	public HtmlAnalyzer(Document document) {
		_document = document;
	}

	public void analyze() {
		scanExistingIds(_document.getDocumentElement());
		scanText(_document.getDocumentElement());
		assignIds();
		cleanIds(_document.getDocumentElement());
		extractText(_document.getDocumentElement());
		updateCrcs(_document.getDocumentElement());
	}

	/**
	 * Updates data-tx attributes to include CRC checksums in "ID:CRC" format.
	 */
	private void updateCrcs(Element element) {
		String id = fetchId(element);
		if (id != null) {
			// Get CRC for this ID (could be from content or attributes)
			String crc = _crcById.get(id);
			if (crc == null) {
				// Check if this ID has only attribute texts
				for (String textId : _crcById.keySet()) {
					if (textId.startsWith(id + ".")) {
						// Use CRC of first attribute text found
						crc = _crcById.get(textId);
						break;
					}
				}
			}

			// Update attribute with ID:CRC format
			if (crc != null) {
				element.setAttribute(ID_ATTR, id + ":" + crc);
			}
		}

		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element sub) {
				updateCrcs(sub);
			}
		}
	}

	public Map<String, String> getTextById() {
		return _textById;
	}

	public void setTextById(Map<String, String> textById) {
		_textById = textById;
	}

	/**
	 * Returns the set of text IDs that existed in the source document before analysis.
	 * These are IDs that were already present as data-tx attributes.
	 */
	public Set<String> getExistingIds() {
		return _existingIds;
	}

	/**
	 * Returns the map of text IDs to their old CRC checksums (from source document).
	 */
	public Map<String, String> getOldCrcById() {
		return _oldCrcById;
	}

	/**
	 * Returns the map of text IDs to their current CRC checksums.
	 */
	public Map<String, String> getCrcById() {
		return _crcById;
	}

	/**
	 * Computes CRC32 checksum of a text string.
	 */
	public static String computeCrc(String text) {
		CRC32 crc = new CRC32();
		crc.update(text.getBytes());
		return Long.toHexString(crc.getValue());
	}

	public void inject() {
		injectText(_document.getDocumentElement());
	}
	
	private void injectText(Element element) {
		String id = fetchId(element);
		if (id != null) {
			NamedNodeMap attributes = element.getAttributes();
			for (int n = 0, cnt = attributes.getLength(); n < cnt; n ++) {
				Node attr = attributes.item(n);
				if (TEXT_ATTRS.contains(attr.getNodeName())) {
					String text = _textById.get(id + "." + attr.getNodeName());
					if (text != null) {
						attr.setNodeValue(text);
					}
				}
			}

			if (!CODE_TAGS.contains(element.getTagName())) {
				String text = _textById.get(id);
				if (text != null) {
					new TextInjector(element).inject(text);
				}
			}
		}

		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element sub) {
				injectText(sub);
			}
		}
	}

	private void extractText(Element element) {
		String id = fetchId(element);
		if (id != null) {
			// Collect all texts for this element to compute combined CRC
			StringBuilder combinedText = new StringBuilder();

			NamedNodeMap attributes = element.getAttributes();
			for (int n = 0, cnt = attributes.getLength(); n < cnt; n ++) {
				Node attr = attributes.item(n);
				if (TEXT_ATTRS.contains(attr.getNodeName())) {
					String attrText = attr.getTextContent();
					if (!attrText.isBlank()) {
						String textId = id + "." + attr.getNodeName();
						_textById.put(textId, attrText);
						combinedText.append(attr.getNodeName()).append("=").append(attrText).append("\n");
					}
				}
			}

			if (!CODE_TAGS.contains(element.getTagName())) {
				// Note: The element could have an ID assigned, because it only contains text attributes.
				if (containsText(element)) {
					String text = new TextExtractor(element).extract();
					_textById.put(id, text);
					combinedText.append("content=").append(text).append("\n");
				}
			}

			// Store combined CRC for the entire element
			if (combinedText.length() > 0) {
				_crcById.put(id, computeCrc(combinedText.toString()));
			}
		}

		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element sub) {
				extractText(sub);
			}
		}
	}

	private void cleanIds(Element element) {
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element sub) {
				if (!_textParents.containsKey(child)) {
					((Element) child).removeAttribute(ID_ATTR);
				}
				cleanIds(sub);
			}
		}
	}

	private void assignIds() {
		for (Element textParent : _textParents.keySet()) {
			if (!hasTextAttribute(textParent) && hasTextParent(textParent)) {
				textParent.removeAttribute(ID_ATTR);
				continue;
			}

			String id = textParent.getAttribute(ID_ATTR);
			if (id != null && !id.isBlank()) {
				if (_elementById.containsKey(id)) {
					// Duplicate assignment, remove.
					textParent.removeAttribute(ID_ATTR);
					id = null;
				}
			}
			if (id == null || id.isBlank()) {
				id = _idFormat.format(_nextId++);
				textParent.setAttribute(ID_ATTR, id);
			}

			_elementById.put(id, textParent);
		}
	}

	private boolean hasTextParent(Element textParent) {
		for (Node parent = textParent.getParentNode(); parent != null; parent = parent.getParentNode()) {
			Boolean containsText = _textParents.get(parent);
			if (containsText != null && containsText.booleanValue()) {
				return true;
			}
		}
		return false;
	}
	
	// <p id="t0040">Some text<b><i>what</i> a great</b> nonsense.</p>

	/**
	 * Scans all existing text node IDs in the given document and computes the next free ID to assign.
	 * Also tracks which IDs existed before analysis and extracts old CRC checksums.
	 */
	private void scanExistingIds(Element element) {
		String id = fetchId(element);
		if (id != null) {
			_existingIds.add(id);

			String crc = fetchCrc(element);
			if (crc != null) {
				_oldCrcById.put(id, crc);
			}

			_nextId = Math.max(_nextId, Integer.parseInt(id.substring(1)) + 1);
		}

		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element sub) {
				scanExistingIds(sub);
			}
		}
	}

	/** Extract just the ID part (without CRC) */
	private String fetchId(Element element) {
		String id = element.getAttribute(ID_ATTR);
		if (id == null || id.isEmpty()) {
			return null;
		}

		int sepIndex = id.indexOf(':');
		if (sepIndex > 0) {
			// Remove checksum from ID.
			return id.substring(0, sepIndex);
		} else {
			return id;
		}
	}

	/** Extract old CRC if present */
	private String fetchCrc(Element element) {
		String id = element.getAttribute(ID_ATTR);
		if (id == null || id.isEmpty()) {
			return null;
		}
		
		int sepIndex = id.indexOf(':');
		if (sepIndex > 0) {
			// Extract CRC.
			return id.substring(sepIndex + 1);
		} else {
			return null;
		}
	}
	
	/**
	 * Scans the given document for elements that contain text (either in user-facing text attributes, or text content).
	 */
	private void scanText(Element element) {
		if (hasTextAttribute(element)) {
			_textParents.put(element, Boolean.FALSE);
		}

		if (CODE_TAGS.contains(element.getTagName())) {
			// No translation here.
			return;
		}

		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Text text) {
				if (hasText(text)) {
					Element textParent = (Element) child.getParentNode();
					_textParents.put(textParent, Boolean.TRUE);
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
