package com.saic.uicds.clients.em.webeocAdapter;

import java.util.Map;

import x0.comEsi911Webeoc7Api1.ArrayOfString;
import x0.comEsi911Webeoc7Api1.GetDataByDataIdResponseDocument;
import x0.comEsi911Webeoc7Api1.GetDataResponseDocument;
import x0.comEsi911Webeoc7Api1.GetFilteredDataResponseDocument;
import x0.comEsi911Webeoc7Api1.WebEOCCredentials;

public interface WebEOCWebServiceClient {

	/**
	 * Get list of incidents from the WebEOC Server.
	 * @return
	 */
	public abstract ArrayOfString getIncidentList();

	/**
	 * Get a list of entries matching the input filter on the input board
	 * @param board
	 * @param filters
	 * @return
	 */
	public abstract GetFilteredDataResponseDocument getFilteredDataFromBoard(
			Board board, Map<String, String> filters) throws WebEOCOperationException;

	/**
	 * Get all data from the input board
	 * @param board
	 * @return
	 */
	public abstract GetDataResponseDocument getDataFromBoard(Board board);
	
	/**
	 * Get a specific board entry.
	 * @param board
	 * @param dataid
	 * @return
	 */
	public abstract GetDataByDataIdResponseDocument getDataByIdFromBoard(Board board, int dataid);

	/**
	 * Add the input data to the board for the input incident
	 * @param board
	 * @param incidentName
	 * @param map
	 * @return
	 */
	public abstract int addDataToBoard(Board board, String incidentName,
			Map<String, String> map) throws WebEOCOperationException;

	/**
	 * Update a data entry for the input board and incident
	 * @param board
	 * @param incidentName
	 * @param dataid
	 * @param map
	 * @return
	 */
	public abstract int updateDataOnBoard(Board board, String incidentName,
			int dataid, Map<String, String> map) throws WebEOCOperationException;

	/**
	 * Get the user name this client is logged into WebEOC with
	 * @return
	 */
	
	public abstract void setCredentialsEx(WebEOCCredentials credentials, Board board);
	
	public String getUserName();
	
	/**
	 * Get the position that this client is using for WebEOC access
	 * @return
	 */
	public String getPosition();
	
	/**
	 * Get the host name of the server
	 * @return
	 */
	public String getServerName();
	
	 
}