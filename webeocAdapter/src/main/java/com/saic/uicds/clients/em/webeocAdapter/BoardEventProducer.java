/**
 * 
 */
package com.saic.uicds.clients.em.webeocAdapter;

/**
 * Interface of classes that can poll WebEOC boards and produce
 * a WebEOC data set that represents the changes in the board
 * since the last time the board was polled.
 * 
 * @author roger
 *
 */
public interface BoardEventProducer {
	/**
	 * Register a listener to receive BoardListener events
	 * @param listener
	 */	
	public void registerListener(BoardListener listener);

	/**
	 * Remove a registered listener.
	 * @param listener
	 */
	public void unregisterListener(BoardListener listener);

}
