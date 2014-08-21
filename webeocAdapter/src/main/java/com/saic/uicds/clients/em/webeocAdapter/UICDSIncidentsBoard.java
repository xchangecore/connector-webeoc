/**
 * 
 */
package com.saic.uicds.clients.em.webeocAdapter;

import java.util.HashMap;
import java.util.Map;

import org.apache.xmlbeans.XmlObject;

/**
 * @author roger
 *
 */
public class UICDSIncidentsBoard extends Board {
	
	private static final String UICDS_SIG_EVENTS_BOARD_NAME = "UICDS Incidents";

	public static final String UICDS_IG_ID_FIELD = "uicdsid";

	public static final String CONTACT_PHONE_FIELD = "contact_phone";

	public static final String CONTACT_NAME_FIELD = "contact_name";

	public static final String JURISDICTION_FIELD = "jurisdiction";
	
	public static final String LABEL_FIELD = "label";

	public static final String LONGITUDE_FIELD = "longitude";

	public static final String LATITUDE_FIELD = "latitude";

	public static final String ADDRESS_FIELD = "address";

	public static final String EVENT_DATETIME_FIELD = "event_datetime";

	public static final String EVENT_TYPE_FIELD = "event_type";

	public static final String DESCRIPTION_FIELD = "description";

	public static final String STATUS_FIELD = "status";
	
	public static final String OPEN_STATUS_VALUE = "Open";
	
	public static final String CLOSED_STATUS_VALUE = "Closed";

//	public static final String INPUT_VIEW_NAME = "Other Events";

//	public static final String VIEW_NAME = "Full Display";

	public UICDSIncidentsBoard() {
	//	setBoardName(UICDS_SIG_EVENTS_BOARD_NAME);
	//	setViewName(VIEW_NAME);
	//	setInputViewName(UICDSIncidentsBoard.INPUT_VIEW_NAME);
	}
	
	public Map<String, String> getFieldMapForCachedIncident(String interestGroupID) {
		XmlObject record = getRecordByInterestID(interestGroupID);
		if (record != null) {
			Map<String, String> map = UICDSIncidentsBoard.copyCurrentData(record);
			return map;
		}
		return new HashMap<String,String>();
	}

	public XmlObject getRecordByInterestID(String interestGroupID) {
		XmlObject item = null;
		
		for (Integer dataid : items.keySet()) {
			XmlObject record = getBoardEntryXmlObject(dataid);;
			if (interestGroupID.equals(WebEOCUtils.getAttributeFromRecord(UICDS_IG_ID_FIELD, record))) {
				return record;
			}
		}
		
		return null;
	}

	public static Map<String, String> copyCurrentData(XmlObject xmlObject) {
		HashMap<String, String> map = new HashMap<String, String>();
		
		String value = WebEOCUtils.getAttributeFromRecord(STATUS_FIELD, xmlObject);
		map.put(STATUS_FIELD, value);
		
		value = WebEOCUtils.getAttributeFromRecord(LABEL_FIELD, xmlObject);
		map.put(LABEL_FIELD, value);
		
		value = WebEOCUtils.getAttributeFromRecord(ADDRESS_FIELD, xmlObject);
		map.put(ADDRESS_FIELD, value);
		
		value = WebEOCUtils.getAttributeFromRecord(CONTACT_NAME_FIELD, xmlObject);
		map.put(CONTACT_NAME_FIELD, value);
	
		value = WebEOCUtils.getAttributeFromRecord(CONTACT_PHONE_FIELD, xmlObject);
		map.put(CONTACT_PHONE_FIELD, value);
	
		value = WebEOCUtils.getAttributeFromRecord(DESCRIPTION_FIELD, xmlObject);
		map.put(DESCRIPTION_FIELD, value);
	
		value = WebEOCUtils.getAttributeFromRecord(EVENT_DATETIME_FIELD, xmlObject);
		map.put(EVENT_DATETIME_FIELD, value);
	
		value = WebEOCUtils.getAttributeFromRecord(EVENT_TYPE_FIELD, xmlObject);
		map.put(EVENT_TYPE_FIELD, value);
	
		value = WebEOCUtils.getAttributeFromRecord(JURISDICTION_FIELD, xmlObject);
		map.put(JURISDICTION_FIELD, value);
	
		value = WebEOCUtils.getAttributeFromRecord(LATITUDE_FIELD, xmlObject);
		map.put(LATITUDE_FIELD, value);
	
		value = WebEOCUtils.getAttributeFromRecord(LONGITUDE_FIELD, xmlObject);
		map.put(LONGITUDE_FIELD, value);
	
		value = WebEOCUtils.getAttributeFromRecord(UICDS_IG_ID_FIELD, xmlObject);
		map.put(UICDS_IG_ID_FIELD, value);
		
		return map;
	}

}
