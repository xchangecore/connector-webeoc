/**
 * 
 */
package com.saic.uicds.clients.em.webeocAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import x0.comEsi911Webeoc7Api1.GetDataResponseDocument;

/**
 * Implementation of a BoardEventProducer
 * @author roger
 *
 */
public class WebEOCEventProducer implements BoardEventProducer {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private WebEOCWebServiceClient webEocClient;
	
	public WebEOCWebServiceClient getWebEocClient() {
		return webEocClient;
	}

	public void setWebEocClient(WebEOCWebServiceClient webEocClient) {
		this.webEocClient = webEocClient;
	}
	
	// Registered listeners key is a unique id for the listener
	private HashMap<String, BoardListener> listeners = new HashMap<String, BoardListener>();
	
	public Map<String, BoardListener> getListeners() {
		return Collections.unmodifiableMap(listeners);
	}
	
	public void initialize() {
		
	}

	/* (non-Javadoc)
	 * @see com.saic.uicds.clients.em.webeocAdapter.BoardEventProducer#registerListener(com.saic.uicds.clients.em.webeocAdapter.BoardListener)
	 */
	@Override
	public void registerListener(BoardListener listener) throws IllegalArgumentException {
		String id = listener.getId();
		if (id == null || id.isEmpty()) 
			throw new IllegalArgumentException("Listener ID is null or empty");
		
		if (listeners.containsKey(id))
			throw new IllegalArgumentException("Listener with ID " + id + " is already registered");

		listeners.put(id, listener);
	}

	/* (non-Javadoc)
	 * @see com.saic.uicds.clients.em.webeocAdapter.BoardEventProducer#unregisterListener(com.saic.uicds.clients.em.webeocAdapter.BoardListener)
	 */
	@Override
	public void unregisterListener(BoardListener listener) {
		String id = listener.getId();
		listeners.remove(id);
	}

	public void pollBoards() {
		// Get the name of each board that needs to be polled and the listeners to be notified
		HashMap<String, ArrayList<BoardListener>> boards = new HashMap<String, ArrayList<BoardListener>>();
		for (String id : listeners.keySet()) {
			if (boards.containsKey(id)) {
				boards.get(listeners.get(id).getBoard().getBoardName()).add(listeners.get(id));
			}
			else {
				ArrayList<BoardListener> boardListeners = new ArrayList<BoardListener>();
				boardListeners.add(listeners.get(id));
				boards.put(listeners.get(id).getBoard().getBoardName(), boardListeners);
			}
		}
		
		// Poll the boards and do notifications
		for (String id : boards.keySet()) {
			GetDataResponseDocument data = webEocClient.getDataFromBoard(boards.get(id).get(0).getBoard());
			if (data != null) {
				for (BoardListener listener : boards.get(id)) {
					listener.handleBoardData(data.getGetDataResponse().getGetDataResult());
				}
			}
		}
	}
}
