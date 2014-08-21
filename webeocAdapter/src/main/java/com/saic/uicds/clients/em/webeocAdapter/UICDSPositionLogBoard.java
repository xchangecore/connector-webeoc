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
public class UICDSPositionLogBoard
    extends Board {

    public static final String UICDS_POSITION_BOARD_NAME = "UICDS Position Log";

    public static final String PRIORITY_FIELD = "Priority";

    public static final String EVENT_TYPE_FIELD = "Event_Type";

    public static final String EVENT_LOCATION_FIELD = "Event_Location";

    public static final String ACTION_FIELD = "Action";

    public static final String NAME_FIELD = "Name_";

    public static final String DATA_TIME_FIELD = "date_time";

    public static final String DESCRIPTION_FIELD = "Description";

    public static final String GLOBALID_FIELD = "globalid";

    public static final String INPUT_VIEW_NAME = "Input - Old";

    public static final String VIEW_NAME = "Position Log All";
    //public static final String VIEW_NAME = "Display - Old";

    public UICDSPositionLogBoard() {

        setBoardName(UICDS_POSITION_BOARD_NAME);
        setViewName(VIEW_NAME);
        setInputViewName(UICDSPositionLogBoard.INPUT_VIEW_NAME);
    }

    public static Map<String, String> copyCurrentData(XmlObject xmlObject) {

        HashMap<String, String> map = new HashMap<String, String>();

        String value = WebEOCUtils.getAttributeFromRecord(PRIORITY_FIELD, xmlObject);
        map.put(PRIORITY_FIELD, value);

        value = WebEOCUtils.getAttributeFromRecord(EVENT_TYPE_FIELD, xmlObject);
        map.put(EVENT_TYPE_FIELD, value);

        value = WebEOCUtils.getAttributeFromRecord(EVENT_LOCATION_FIELD, xmlObject);
        map.put(EVENT_LOCATION_FIELD, value);

        value = WebEOCUtils.getAttributeFromRecord(ACTION_FIELD, xmlObject);
        map.put(ACTION_FIELD, value);

        value = WebEOCUtils.getAttributeFromRecord(NAME_FIELD, xmlObject);
        map.put(NAME_FIELD, value);

        value = WebEOCUtils.getAttributeFromRecord(DATA_TIME_FIELD, xmlObject);
        map.put(DATA_TIME_FIELD, value);

        value = WebEOCUtils.getAttributeFromRecord(DESCRIPTION_FIELD, xmlObject);
        map.put(DESCRIPTION_FIELD, value);

        value = WebEOCUtils.getAttributeFromRecord(GLOBALID_FIELD, xmlObject);
        map.put(GLOBALID_FIELD, value);

        return map;
    }

}
