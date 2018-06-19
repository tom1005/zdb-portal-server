package com.zdb.core.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * A default implementation of {@link Exchange}
 *
 * @version
 */
public final class DefaultExchange implements Exchange {

	private Map<String, Object> properties;

	public Object getProperty(String name) {
		if (properties != null) {
			return properties.get(name);
		}
		return null;
	}

	public Object getProperty(String name, Object defaultValue) {
		Object answer = getProperty(name);
		return answer != null ? answer : defaultValue;
	}

	@SuppressWarnings("unchecked")
	public <T> T getProperty(String name, Class<T> type) {
		Object value = getProperty(name);
		if (value == null) {
			// lets avoid NullPointerException when converting to boolean for null values
			if (boolean.class == type) {
				return (T) Boolean.FALSE;
			}
			return null;
		}

		// eager same instance type test to avoid the overhead of invoking the type converter
		// if already same type
		if (type.isInstance(value)) {
			return (T) value;
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public <T> T getProperty(String name, Object defaultValue, Class<T> type) {
		Object value = getProperty(name, defaultValue);
		if (value == null) {
			// lets avoid NullPointerException when converting to boolean for null values
			if (boolean.class == type) {
				return (T) Boolean.FALSE;
			}
			return null;
		}

		// eager same instance type test to avoid the overhead of invoking the type converter
		// if already same type
		if (type.isInstance(value)) {
			return (T) value;
		}

		return null;
	}

	public void setProperty(String name, Object value) {
		if (value != null) {
			// avoid the NullPointException
			getProperties().put(name, value);
		} else {
			// if the value is null, we just remove the key from the map
			if (name != null) {
				getProperties().remove(name);
			}
		}
	}

	public Object removeProperty(String name) {
		if (!hasProperties()) {
			return null;
		}
		return getProperties().remove(name);
	}

	public Map<String, Object> getProperties() {
		if (properties == null) {
			properties = createProperties();
		}
		return properties;
	}

	public boolean hasProperties() {
		return properties != null && !properties.isEmpty();
	}

	public void setProperties(Map<String, Object> properties) {
		this.properties = properties;
	}

	protected Map<String, Object> createProperties() {
		return new HashMap<>();
	}

	protected Map<String, Object> createProperties(Map<String, Object> properties) {
		return new HashMap<>(properties);
	}

}
