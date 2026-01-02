package de.haumacher.autotranslate.html.extract;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.List;
import java.util.Map;

public class PropertiesWriter {

	private OutputStream _out;
	private Charset _charset;

	public PropertiesWriter(OutputStream out, Charset charset) {
		_out = out;
		_charset = charset;
	}

	public void write(Map<String, String> properties) {
		CharsetEncoder encoder = _charset.newEncoder();
		try (PrintWriter w = new PrintWriter(new OutputStreamWriter(_out, _charset))) {
			List<String> keysSorted = properties.keySet().stream().sorted().toList();
			for (String id : keysSorted) {
				String value = properties.get(id);
				
				write(w, encoder, id);
				w.print("=");
				write(w, encoder, value);
				w.println();
			}
			w.println();
		}
	}

	private void write(PrintWriter w, CharsetEncoder encoder, String str) {
		if (encoder.canEncode(str)) {
			w.print(str);
		} else {
			escape(w, encoder, str);
		}
	}

	private void escape(PrintWriter w, CharsetEncoder encoder, String str) {
		for (int n = 0, length = str.length(); n < length; n++) {
			char ch = str.charAt(n);
			if (encoder.canEncode(ch)) {
				w.print(ch);
			} else {
				w.print("\\u");
				String hex = Integer.toHexString(ch);
				for (int p = hex.length(); p < 4; p++) {
					w.print('0');
				}
				w.print(hex);
			}
		}
	}
}
