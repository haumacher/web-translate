package de.haumacher.autotranslate.html.extract;

import java.util.ArrayList;
import java.util.List;

public class Stack<T> {

	private final List<T> _elements = new ArrayList<>();

	public void push(T element) {
		_elements.add(element);
	}

	public T top() {
		return _elements.get(_elements.size() - 1);
	}

	public T pop() {
		return _elements.remove(_elements.size() - 1);
	}

	public boolean hasTop() {
		return !isEmpty();
	}

	public boolean isEmpty() {
		return _elements.isEmpty();
	}

	public boolean contains(T element) {
		return _elements.contains(element);
	}
	
}
