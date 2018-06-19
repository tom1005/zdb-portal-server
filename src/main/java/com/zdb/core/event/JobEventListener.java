package com.zdb.core.event;

/**
 * @author 06919
 *
 */
public interface JobEventListener {

	/**
	 * @param message
	 */
	public void done(String message);

	/**
	 * @param message
	 */
	public void running(String message);
	
	/**
	 * @param t
	 * @param message
	 */
	public void failure(Throwable t, String message);

}
