package de.haumacher.webtranslate.extract;

import java.util.ArrayList;
import java.util.List;

public class Stack<T> {
	
	private final List<T> elements = new ArrayList<>();

	public void push(T element) {
		elements.add(element);
	}

	public T top() {
		return elements.get(elements.size() - 1);
	}

	public T pop() {
		return elements.remove(elements.size() - 1);
	}

	public boolean hasTop() {
		return !isEmpty();
	}
	
	public boolean isEmpty() {
		return elements.isEmpty();
	}

	public boolean contains(T element) {
		return elements.contains(element);
	}
	
}
