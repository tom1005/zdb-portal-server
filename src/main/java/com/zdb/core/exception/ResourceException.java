package com.zdb.core.exception;

public class ResourceException extends Exception {

	private static final long serialVersionUID = 2800990333710490224L;

	public ResourceException() {
		super();
	}

	public ResourceException(String message) {
		super(message);
	}

	@Override
	public String getMessage() {
		return super.getMessage();
	}
}
