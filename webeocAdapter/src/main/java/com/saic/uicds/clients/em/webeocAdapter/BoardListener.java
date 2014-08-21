/**
 * 
 */
package com.saic.uicds.clients.em.webeocAdapter;

/**
 * Interface of a class that listens for updated items
 * from a WebEOC board.
 * 
 * @author roger
 *
 */
public interface BoardListener {
	
	public Board getBoard();

	public void setBoard(Board board);

	public String getId();

	public void setId(String id);

	/**
	 * Listeners will have this method called each time the poller
	 * gets the list of data items from the board.
	 * 
	 * @param data WebEOC data element
	 */
	public void handleBoardData(String data);
	
}
