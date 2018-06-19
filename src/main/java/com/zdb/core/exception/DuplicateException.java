package com.zdb.core.exception;

public class DuplicateException extends Exception {

	private static final long serialVersionUID = 2800990333710490224L;

	public DuplicateException() {
		super();
	}

	public DuplicateException(String message) {
		super(message);
	}

	@Override
	public String getMessage() {
		return super.getMessage();
	}
}
