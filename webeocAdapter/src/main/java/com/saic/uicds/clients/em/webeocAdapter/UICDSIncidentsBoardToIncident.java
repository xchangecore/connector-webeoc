/**
 * 
 */
package com.saic.uicds.clients.em.webeocAdapter;

import gov.niem.niem.niemCore.x20.ActivityDateDocument;
import gov.niem.niem.niemCore.x20.AddressFullTextDocument;
import gov.niem.niem.niemCore.x20.DateTimeDocument;
import gov.niem.niem.niemCore.x20.DateType;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import javax.xml.namespace.QName;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uicds.incident.IncidentDocument;
import org.uicds.incident.UICDSIncidentType;
import org.uicds.incidentManagementService.ArchiveIncidentRequestDocument;
import org.uicds.incidentManagementService.ArchiveIncidentResponseDocument;
import org.uicds.incidentManagementService.CloseIncidentRequestDocument;
import org.uicds.incidentManagementService.CloseIncidentResponseDocument;
import org.uicds.incidentManagementService.CreateIncidentRequestDocument;
import org.uicds.incidentManagementService.CreateIncidentResponseDocument;
import org.uicds.incidentManagementService.GetIncidentListRequestDocument;
import org.uicds.incidentManagementService.GetIncidentListResponseDocument;
import org.uicds.incidentManagementService.UpdateIncidentRequestDocument;
import org.uicds.incidentManagementService.UpdateIncidentResponseDocument;

import com.saic.precis.x2009.x06.base.IdentificationType;
import com.saic.precis.x2009.x06.base.IdentifierType;
import com.saic.precis.x2009.x06.base.ProcessingStateType;
import com.saic.precis.x2009.x06.base.ProcessingStateType.Enum;
import com.saic.precis.x2009.x06.base.PropertiesType;
import com.saic.precis.x2009.x06.structures.WorkProductDocument.WorkProduct;
import com.saic.uicds.clients.em.async.UicdsCore;
import com.saic.uicds.clients.em.async.UicdsIncident;
import com.saic.uicds.clients.util.Common;

/**
 * A board listener for the UICDS Incidents board. Creates an incident on the
 * configured core for each new entry in the UICDS Incidents board that does not
 * have a uicdsid value.
 * 
 * @author roger
 * 
 */
public class UICDSIncidentsBoardToIncident implements BoardListener {

	private class UpdateAfterCreationRecord {

		public XmlObject record;
		public String interestGroupID;

		UpdateAfterCreationRecord(XmlObject record, String interestGroupID) {

			this.record = record;
			this.interestGroupID = interestGroupID;
		}
	};

	public static final int INTEREST_GROUP_ID_LENGTH = 39;

	public static final String INTEREST_GROUP_ID_PREFIX = "IG-";

	public static final String UICDS_INCIDENTS_BOARD = "UICDS Incidents";

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private UicdsCore uicdsCore;

	private WebEOCEventProducer webEOCEventProducer;

	private UICDSIncidentsBoard uicdsIncidentsBoard;

	private String id;

	private ArrayList<UpdateAfterCreationRecord> updateAfterCreationQueue = new ArrayList<UpdateAfterCreationRecord>();

	private ArrayList<XmlObject> closeQueue = new ArrayList<XmlObject>();

	public UicdsCore getUicdsCore() {

		return uicdsCore;
	}

	public void setUicdsCore(UicdsCore uicdsCore) {

		this.uicdsCore = uicdsCore;
	}

	public WebEOCEventProducer getWebEOCEventProducer() {

		return webEOCEventProducer;
	}

	public void setWebEOCEventProducer(WebEOCEventProducer webEOCEventProducer) {

		this.webEOCEventProducer = webEOCEventProducer;
	}

	public UICDSIncidentsBoard getUicdsIncidentsBoard() {

		return uicdsIncidentsBoard;
	}

	public void setUicdsIncidentsBoard(UICDSIncidentsBoard board) {

		uicdsIncidentsBoard = board;
	}

	public Board getBoard() {

		return uicdsIncidentsBoard;
	}

	public void setBoard(Board board) {

		if (board instanceof UICDSIncidentsBoard) {
			uicdsIncidentsBoard = (UICDSIncidentsBoard) board;
		} else {
			logger.error("Board not set with a UICDSIncidentsBoard type board");
		}
	}

	@Override
	public String getId() {

		return id;
	}

	@Override
	public void setId(String id) {

		this.id = id;
	}

	public int sizeOfUpdateAfterCreationQueue() {

		return updateAfterCreationQueue.size();
	}

	public int sizeOfCloseQueue() {

		return closeQueue.size();
	}

	public void initialize() {

		webEOCEventProducer.registerListener(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.saic.uicds.clients.em.webeocAdapter.BoardListener#handleBoardUpdates
	 * (java.lang.String)
	 */
	@Override
	public void handleBoardData(String data) {

		// Handle any queued data
		processQueuedData();

		// update the boards cached items from the latest data and get a list of
		// updated items
		ArrayList<XmlObject> updatedItems = uicdsIncidentsBoard
				.getListOfUpdatedItems(data);

		// handle each updated or new item
		for (XmlObject record : updatedItems) {

			// Process record based on whether or not the WebEOC adapter last
			// updated the record
			if (WebEOCUtils.lastUpdatedByWebEOCAdapter(record,
					webEOCEventProducer.getWebEocClient().getPosition())) {
				processUpdateByWebEOCAdapter(record);
			} else {
				processUpdateByOtherWebEOCUser(record);
			}

		}
	}

	private void processQueuedData() {

		// TODO: these probably need to check if the record was updated by
		// another user
		// before applying this update.

		// Check if there are any updates that got queued for processing
		if (updateAfterCreationQueue.size() > 0) {
			ArrayList<UpdateAfterCreationRecord> queue = new ArrayList<UpdateAfterCreationRecord>(
					updateAfterCreationQueue);
			updateAfterCreationQueue.clear();
			for (UpdateAfterCreationRecord update : queue) {
				try {
					updateWebEOCEntryAfterCreation(update.record,
							update.interestGroupID);
				} catch (WebEOCOperationException e) {
					updateAfterCreationQueue.add(update);
				}
			}
		}

		// Check if there are any updates that got queued for processing
		if (closeQueue.size() > 0) {
			ArrayList<XmlObject> queue = new ArrayList<XmlObject>(closeQueue);
			closeQueue.clear();
			for (XmlObject close : queue) {
				try {
					closeWebEOCEntry(close);
				} catch (WebEOCOperationException e) {
					closeQueue.add(close);
				}
			}
		}
	}

	private void processUpdateByOtherWebEOCUser(XmlObject record) {

		String interestGroupID = WebEOCUtils.getUicdsIDFromRecord(record);

		// If the entry has no UICDS ID then create a new incident
		if (interestGroupID == null || interestGroupID.isEmpty()) {
			interestGroupID = createNewIncident(record);
		} else {
			WorkProduct incidentWorkProduct = getIncidentDocument(interestGroupID);
			String status = WebEOCUtils.getAttributeFromRecord(
					UICDSIncidentsBoard.STATUS_FIELD, record);

			// If the incident work product is found on the core then update the
			// incident
			if (incidentWorkProduct != null) {
				if (status
						.equalsIgnoreCase(UICDSIncidentsBoard.OPEN_STATUS_VALUE)) {
					logger.debug("Processing update fom other user by updating incident on core");
					updateOnCore(incidentWorkProduct, record);
				} else {
					closeAndArchiveIncident(incidentWorkProduct);
				}
			}
			// This state means that either we are starting back up and this
			// record was
			// potentially updated while offline with the core and the incident
			// was removed
			// from the core. Or a user has created or updated an entry and
			// filled or
			// edited the UICDS ID field.
			// So assume that if the data in the uicdsid field looks like a
			// UICDS ID
			// that the incident has gone away and close the entry. If it
			// doesn't look
			// like a UICDS ID then create a new incident an overwrite the value
			// in the
			// uicdsid field.
			else {
				if (interestGroupID.startsWith(INTEREST_GROUP_ID_PREFIX)
						&& interestGroupID.length() == INTEREST_GROUP_ID_LENGTH) {
					if (status
							.equalsIgnoreCase(UICDSIncidentsBoard.OPEN_STATUS_VALUE)) {
						try {
							closeWebEOCEntry(record);
						} catch (WebEOCOperationException e) {
							closeQueue.add(record);
						}
					}
				} else {
					interestGroupID = createNewIncident(record);
				}
			}
		}
	}

	private void processUpdateByWebEOCAdapter(XmlObject record) {

		String interestGroupID = WebEOCUtils.getUicdsIDFromRecord(record);
		String status = WebEOCUtils.getAttributeFromRecord(
				UICDSIncidentsBoard.STATUS_FIELD, record);

		// Should never have an entry created by the WebEOC adapter with no
		// UICDS ID
		if (interestGroupID == null || interestGroupID.isEmpty()) {
			logger.error("Found a record that was created by the WebEOC Adapter with no UICDS ID - This shouldn't happen.");
		}
		// Try to get the current incident work product and update the incident
		// if it is found
		else {
			WorkProduct incidentWorkProduct = getIncidentDocument(interestGroupID);
			if (incidentWorkProduct == null) {
				if (status
						.equalsIgnoreCase(UICDSIncidentsBoard.OPEN_STATUS_VALUE)) {
					try {
						closeWebEOCEntry(record);
					} catch (WebEOCOperationException e) {
						closeQueue.add(record);
					}
				}
			}
		}

	}

	private void closeWebEOCEntry(XmlObject record)
			throws WebEOCOperationException {

		HashMap<String, String> fieldMap = WebEOCUtils
				.getFieldMapFromRecord(record);
		fieldMap.put(UICDSIncidentsBoard.STATUS_FIELD,
				UICDSIncidentsBoard.CLOSED_STATUS_VALUE);
		String dataidString = WebEOCUtils.getAttributeFromRecord(
				Board.DATAID_FIELD, record);
		try {
			int dataid = Integer.parseInt(dataidString);
			logger.debug("Closing entry " + dataid);
			webEOCEventProducer.getWebEocClient().updateDataOnBoard(
					uicdsIncidentsBoard, uicdsIncidentsBoard.getIncidentName(),
					dataid, fieldMap);
		} catch (NumberFormatException e) {
			logger.error("Error getting data id from a record when trying to close the record");
		}
	}

	private String createNewIncident(XmlObject record) {

		String interestGroupID;
		logger.debug("Creating new incident");
		interestGroupID = createNewIncidentFromWebEOCRecord(record);

		// Update the record with the new Interest Group ID
		if (interestGroupID != null) {
			try {
				updateWebEOCEntryAfterCreation(record, interestGroupID);
			} catch (WebEOCOperationException e) {
				updateAfterCreationQueue.add(new UpdateAfterCreationRecord(
						record, interestGroupID));
			}
		} else {
			logger.error("Cannot set UICDS Interest Group ID in the WebEOC Entry because it was null");
		}
		return interestGroupID;
	}

	private WorkProduct getIncidentDocument(String interestGroupID) {

		GetIncidentListRequestDocument listRequest = GetIncidentListRequestDocument.Factory
				.newInstance();
		listRequest.addNewGetIncidentListRequest();
		try {
			GetIncidentListResponseDocument response = (GetIncidentListResponseDocument) uicdsCore
					.marshalSendAndReceive(listRequest);
			if (response.getGetIncidentListResponse().getWorkProductList()
					.sizeOfWorkProductArray() == 0) {
				return null;
			}

			IdentificationType wpid = null;
			for (WorkProduct workProduct : response
					.getGetIncidentListResponse().getWorkProductList()
					.getWorkProductArray()) {
				PropertiesType properties = Common
						.getPropertiesElement(workProduct);
				if (properties != null
						&& properties.getAssociatedGroups()
								.sizeOfIdentifierArray() > 0
						&& properties.getAssociatedGroups()
								.getIdentifierArray(0).getStringValue()
								.equals(interestGroupID)) {
					wpid = Common.getIdentificationElement(workProduct);
					break;
				}
			}

			if (wpid != null) {
				WorkProduct wp = uicdsCore.getWorkProductFromCore(wpid);
				return wp;
			}
		} catch (ClassCastException e) {
			logger.error("Error casting response to GetIncidentListResponseDocument: "
					+ e.getMessage());
		}

		return null;
	}

	private IncidentDocument getIncidentDocumentFromWorkProduct(WorkProduct wp) {

		IncidentDocument incidentDocument = null;
		XmlObject[] objects = wp.getStructuredPayloadArray(0).selectChildren(
				new QName(UicdsIncident.INCIDENT_SERVICE_NS,
						UicdsIncident.INCIDENT_ELEMENT_NAME));
		if (objects.length > 0) {
			try {
				incidentDocument = IncidentDocument.Factory.parse(objects[0]
						.getDomNode());
			} catch (XmlException e) {
				logger.error("Error parsing IncidentDocument from payload: "
						+ e.getMessage());
			}
		}
		return incidentDocument;
	}

	private void updateWebEOCEntryAfterCreation(XmlObject record,
			String interestGroupID) throws WebEOCOperationException {

		String dataidString = WebEOCUtils.getAttributeFromRecord(
				Board.DATAID_FIELD, record);
		if (dataidString != null && !dataidString.isEmpty()) {
			try {
				int dataid = Integer.parseInt(dataidString);
				HashMap<String, String> fieldMap = WebEOCUtils
						.getFieldMapFromRecord(record);
				fieldMap.put(UICDSIncidentsBoard.UICDS_IG_ID_FIELD,
						interestGroupID);

				// Make sure the entry has an open status when created.
				String status = WebEOCUtils.getAttributeFromRecord(
						UICDSIncidentsBoard.STATUS_FIELD, record);
				if (status == null
						|| status.isEmpty()
						|| !status
								.equalsIgnoreCase(UICDSIncidentsBoard.OPEN_STATUS_VALUE)) {
					fieldMap.put(UICDSIncidentsBoard.STATUS_FIELD,
							UICDSIncidentsBoard.OPEN_STATUS_VALUE);
				}

				logger.debug("updating entry " + dataid + " on board "
						+ uicdsIncidentsBoard.getBoardName());
				webEOCEventProducer.getWebEocClient()
						.updateDataOnBoard(uicdsIncidentsBoard,
								uicdsIncidentsBoard.getIncidentName(), dataid,
								fieldMap);

			} catch (NumberFormatException e) {
				logger.error("dataid cannot be parsed into integer when updating WebEOC entry: "
						+ dataidString);
			}
		} else {
			logger.error("dataid not found when updating WebEOC entry");
		}
	}

	private String createNewIncidentFromWebEOCRecord(XmlObject record) {

		UICDSIncidentType incident = UICDSIncidentType.Factory.newInstance();

		setActivityCategory(incident, record);
		setActivityName(incident, record);
		setActivityDescription(incident, record);
		setActivityDate(incident, record);
		setIncidentAddressLocation(incident, record);
		setIncidentGeoLocation(incident, record);
		setJurisdictionInformation(incident, record);
		incident = WebEOCUtils.addIncidentEvent(incident,
				Constants.WEBEOC_CREATED_REASON, record, uicdsIncidentsBoard,
				webEOCEventProducer.getWebEocClient().getServerName());

		return createOnCore(incident);
	}

	private void setActivityCategory(UICDSIncidentType incident,
			XmlObject record) {

		String type = WebEOCUtils.getAttributeFromRecord(
				UICDSIncidentsBoard.EVENT_TYPE_FIELD, record);
		if (incident.sizeOfActivityCategoryTextArray() < 1) {
			incident.addNewActivityCategoryText();
		}
		if (type != null && !type.isEmpty()) {
			incident.getActivityCategoryTextArray(0).setStringValue(type);
		}
	}

	private void setActivityName(UICDSIncidentType incident, XmlObject record) {

		String label = WebEOCUtils.getAttributeFromRecord(
				UICDSIncidentsBoard.LABEL_FIELD, record);
		if (label != null && !label.isEmpty()) {
			if (incident.sizeOfActivityNameArray() < 1) {
				incident.addNewActivityName();
			}
			incident.getActivityNameArray(0).setStringValue(label);
		} else {
			String desc = WebEOCUtils.getAttributeFromRecord(
					UICDSIncidentsBoard.DESCRIPTION_FIELD, record);
			if (incident.sizeOfActivityNameArray() < 1) {
				incident.addNewActivityName();
			}
			if (desc != null && !desc.isEmpty()) {
				int size = 20;
				if (size > desc.length()) {
					size = desc.length();
				}
				incident.getActivityNameArray(0).setStringValue(
						desc.substring(0, size));
			} else {
				String dataid = WebEOCUtils.getAttributeFromRecord(
						Board.DATAID_FIELD, record);
				incident.getActivityNameArray(0)
						.setStringValue(
								uicdsIncidentsBoard.getBoardName() + " entry "
										+ dataid);
			}
		}
	}

	private void setActivityDescription(UICDSIncidentType incident,
			XmlObject record) {

		if (incident.sizeOfActivityDescriptionTextArray() < 1) {
			incident.addNewActivityDescriptionText();
		}
		incident.getActivityDescriptionTextArray(0).setStringValue(
				WebEOCUtils.getAttributeFromRecord(
						UICDSIncidentsBoard.DESCRIPTION_FIELD, record));
	}

	private void setJurisdictionInformation(UICDSIncidentType incident,
			XmlObject record) {

		String jurisdiction = WebEOCUtils.getAttributeFromRecord(
				UICDSIncidentsBoard.JURISDICTION_FIELD, record);
		if (incident.sizeOfIncidentJurisdictionalOrganizationArray() < 1) {
			incident.addNewIncidentJurisdictionalOrganization();
		}
		if (incident.getIncidentJurisdictionalOrganizationArray(0)
				.sizeOfOrganizationNameArray() < 1) {
			incident.getIncidentJurisdictionalOrganizationArray(0)
					.addNewOrganizationName();
		}
		incident.getIncidentJurisdictionalOrganizationArray(0)
				.getOrganizationNameArray(0).setStringValue(jurisdiction);

		String contact_name = WebEOCUtils.getAttributeFromRecord(
				UICDSIncidentsBoard.CONTACT_NAME_FIELD, record);

		if (incident.getIncidentJurisdictionalOrganizationArray(0)
				.sizeOfOrganizationPrincipalOfficialArray() < 1) {
			incident.getIncidentJurisdictionalOrganizationArray(0)
					.addNewOrganizationPrincipalOfficial();
		}
		if (incident.getIncidentJurisdictionalOrganizationArray(0)
				.getOrganizationPrincipalOfficialArray(0)
				.sizeOfPersonNameArray() < 1) {
			incident.getIncidentJurisdictionalOrganizationArray(0)
					.getOrganizationPrincipalOfficialArray(0)
					.addNewPersonName();
		}
		if (incident.getIncidentJurisdictionalOrganizationArray(0)
				.getOrganizationPrincipalOfficialArray(0).getPersonNameArray(0)
				.sizeOfPersonFullNameArray() < 1) {
			incident.getIncidentJurisdictionalOrganizationArray(0)
					.getOrganizationPrincipalOfficialArray(0)
					.getPersonNameArray(0).addNewPersonFullName();
		}
		incident.getIncidentJurisdictionalOrganizationArray(0)
				.getOrganizationPrincipalOfficialArray(0).getPersonNameArray(0)
				.getPersonFullNameArray(0).setStringValue(contact_name);

		String contact_phone = WebEOCUtils.getAttributeFromRecord(
				UICDSIncidentsBoard.CONTACT_PHONE_FIELD, record);
	}

	private void setIncidentGeoLocation(UICDSIncidentType incident,
			XmlObject record) {

		String latitude = WebEOCUtils.getAttributeFromRecord(
				UICDSIncidentsBoard.LATITUDE_FIELD, record);
		String longitude = WebEOCUtils.getAttributeFromRecord(
				UICDSIncidentsBoard.LONGITUDE_FIELD, record);

		if (incident.sizeOfIncidentLocationArray() == 0) {
			incident.addNewIncidentLocation();
		}
		if (incident.getIncidentLocationArray(0).sizeOfLocationAreaArray() < 1) {
			incident.getIncidentLocationArray(0).addNewLocationArea();
		}
		if (incident.getIncidentLocationArray(0).getLocationAreaArray(0)
				.sizeOfAreaCircularRegionArray() < 1) {
			incident.getIncidentLocationArray(0).getLocationAreaArray(0)
					.addNewAreaCircularRegion();
		}

		incident.getIncidentLocationArray(0).getLocationAreaArray(0)
				.getAreaCircularRegionArray(0)
				.set(Common.createCircle(latitude, longitude));
	}

	private void setIncidentAddressLocation(UICDSIncidentType incident,
			XmlObject record) {

		String address = WebEOCUtils.getAttributeFromRecord(
				UICDSIncidentsBoard.ADDRESS_FIELD, record);

		AddressFullTextDocument addressDocument = AddressFullTextDocument.Factory
				.newInstance();
		addressDocument.addNewAddressFullText().setStringValue(address);

		if (incident.sizeOfIncidentLocationArray() < 1) {
			incident.addNewIncidentLocation().addNewLocationAddress();
		}
		if (incident.getIncidentLocationArray(0).sizeOfLocationAddressArray() < 1) {
			incident.getIncidentLocationArray(0).addNewLocationAddress();
		}
		incident.getIncidentLocationArray(0).getLocationAddressArray(0)
				.set(addressDocument);
	}

	private void setActivityDate(UICDSIncidentType incident, XmlObject record) {

		String event_datetime = WebEOCUtils.getAttributeFromRecord(
				UICDSIncidentsBoard.EVENT_DATETIME_FIELD, record);
		String dateString = getDate(event_datetime);

		DateTimeDocument dateDoc = DateTimeDocument.Factory.newInstance();
		dateDoc.addNewDateTime().setStringValue(dateString);

		ActivityDateDocument activityDate = ActivityDateDocument.Factory
				.newInstance();
		activityDate.addNewActivityDate().set(dateDoc);

		if (incident.sizeOfActivityDateRepresentationArray() < 1) {
			Common.substitute(incident.addNewActivityDateRepresentation(),
					Common.NIEM_NS, Common.ACTIVITY_DATE, DateType.type,
					activityDate.getActivityDate());
		} else {
			incident.getActivityDateRepresentationArray(0).set(
					activityDate.getActivityDate());
		}
	}

	private String getDate(String event_datetime) {

		String dateTimeValue = null;
		if (event_datetime != null && event_datetime.length() > 0) {
			SimpleDateFormat ISO8601Local = new SimpleDateFormat(
					"MM/dd/yyyy HH:mm:ss");
			TimeZone timeZone = TimeZone.getDefault();
			ISO8601Local.setTimeZone(timeZone);
			try {
				Date dateTime = (Date) ISO8601Local
						.parse(event_datetime.trim());
				SimpleDateFormat newDateFormat = new SimpleDateFormat(
						"yyyy-MM-dd'T'HH:mm:ss");
				newDateFormat.setTimeZone(timeZone);
				dateTimeValue = newDateFormat.format(dateTime);
				return dateTimeValue;
			} catch (ParseException e) {
				System.err
						.println("Error parsing date string should be yyyy-MM-dd'T'HH:mm:ss format: "
								+ e.getMessage());
			}
		}
		return dateTimeValue;
	}

	/**
	 * Close and archive the input incident
	 * 
	 * @param incident
	 */
	private void closeAndArchiveIncident(WorkProduct incidentWorkProduct) {

		IdentifierType incidentID = Common
				.getFirstAssociatedInterestGroup(incidentWorkProduct);
		if (incidentID != null && closeIncident(incidentID)) {
			if (archiveIncident(incidentID)) {
				logger.info("Incident " + incidentID.getStringValue()
						+ " closed and archived");
			} else {
				logger.error("Error archiving incident "
						+ incidentID.getStringValue());
			}
		} else {
			logger.error("Error closing incident "
					+ incidentID.getStringValue());
		}
	}

	private boolean closeIncident(IdentifierType incidentID) {

		CloseIncidentRequestDocument request = CloseIncidentRequestDocument.Factory
				.newInstance();
		request.addNewCloseIncidentRequest().setIncidentID(
				incidentID.getStringValue());

		try {
			CloseIncidentResponseDocument response = (CloseIncidentResponseDocument) uicdsCore
					.marshalSendAndReceive(request);

			Enum status = response.getCloseIncidentResponse()
					.getWorkProductProcessingStatus().getStatus();

			if (status == ProcessingStateType.REJECTED) {
				logger.error("Closing of incident was REJECTED");
				return false;
			} else if (status == ProcessingStateType.PENDING) {
				logger.error("Closing of incident is PENDING");
				return false;
			}
		} catch (ClassCastException e) {
			logger.error("Error casting response to CloseIncidentResponseDocument: "
					+ e.getMessage());
		}

		return true;
	}

	private boolean archiveIncident(IdentifierType incidentID) {

		ArchiveIncidentRequestDocument request = ArchiveIncidentRequestDocument.Factory
				.newInstance();
		request.addNewArchiveIncidentRequest().setIncidentID(
				incidentID.getStringValue());

		try {
			ArchiveIncidentResponseDocument response = (ArchiveIncidentResponseDocument) uicdsCore
					.marshalSendAndReceive(request);

			Enum status = response.getArchiveIncidentResponse()
					.getWorkProductProcessingStatus().getStatus();

			if (status == ProcessingStateType.REJECTED) {
				logger.error("Archive of incident was REJECTED");
				return false;
			} else if (status == ProcessingStateType.PENDING) {
				logger.error("Archive of incident is PENDING");
				return false;
			}
		} catch (ClassCastException e) {
			logger.error("Error casting response to ArchiveIncidentResponseDocument: "
					+ e.getMessage());
		}

		return true;
	}

	/**
	 * Update the incident on the core
	 * 
	 * @param incidentWorkProduct
	 */
	private void updateOnCore(WorkProduct incidentWorkProduct, XmlObject record) {

		IncidentDocument incidentDocument = getIncidentDocumentFromWorkProduct(incidentWorkProduct);
		UICDSIncidentType incident = incidentDocument.getIncident();

		// Update the fields from the WebEOC entry
		setActivityCategory(incident, record);
		setActivityName(incident, record);
		setActivityDescription(incident, record);
		setActivityDate(incident, record);
		setIncidentAddressLocation(incident, record);
		setIncidentGeoLocation(incident, record);
		setJurisdictionInformation(incident, record);

		incidentWorkProduct.getStructuredPayloadArray(0).set(incidentDocument);
		UpdateIncidentRequestDocument request = UpdateIncidentRequestDocument.Factory
				.newInstance();
		request.addNewUpdateIncidentRequest().setIncident(
				getIncidentDocumentFromWorkProduct(incidentWorkProduct)
						.getIncident());
		request.getUpdateIncidentRequest().addNewWorkProductIdentification()
				.set(Common.getIdentificationElement(incidentWorkProduct));

		logger.debug("Updating incident "
				+ incident.getActivityIdentificationArray(0)
						.getIdentificationIDArray(0).getStringValue());

		try {
			UpdateIncidentResponseDocument response = (UpdateIncidentResponseDocument) uicdsCore
					.marshalSendAndReceive(request);

			Enum status = response.getUpdateIncidentResponse()
					.getWorkProductPublicationResponse()
					.getWorkProductProcessingStatus().getStatus();
			// If the update was rejected then try again the next time around
			if (status == ProcessingStateType.REJECTED) {
				logger.error("Update of incident was REJECTED");
			}
			// If pending then check status again when the notification of final
			// status arrives from
			// notification
			else if (status == ProcessingStateType.PENDING) {
				logger.error("Update of incident is PENDING");
			}
		} catch (ClassCastException e) {
			logger.error("Error casting response to UpdateIncidentResponseDocument");
			// } catch (InterruptedException e) {
			logger.error("Thread sleep error: " + e.getMessage());
		}

	}

	/**
	 * Create an incident on the associated core with the input
	 * UICDSIncidentType
	 * 
	 * @param incident
	 * @return
	 */
	private String createOnCore(UICDSIncidentType incident) {

		String incidentID = null;
		CreateIncidentRequestDocument request = CreateIncidentRequestDocument.Factory
				.newInstance();
		request.addNewCreateIncidentRequest().setIncident(incident);
		logger.debug("Incidents Board incident: " + incident.toString());
		try {
			CreateIncidentResponseDocument response = (CreateIncidentResponseDocument) uicdsCore
					.marshalSendAndReceive(request);

			Enum status = response.getCreateIncidentResponse()
					.getWorkProductPublicationResponse()
					.getWorkProductProcessingStatus().getStatus();

			// If the create was accepted then get the id and all the associated
			// documents
			if (status == ProcessingStateType.ACCEPTED) {

				// Get the incident id
				PropertiesType properties = Common
						.getPropertiesElement(response
								.getCreateIncidentResponse()
								.getWorkProductPublicationResponse()
								.getWorkProduct());
				if (properties != null
						&& properties.getAssociatedGroups()
								.sizeOfIdentifierArray() > 0) {
					incidentID = properties.getAssociatedGroups()
							.getIdentifierArray(0).getStringValue();
					logger.debug("Incident: " + incidentID + " created ...");
				} else {
					logger.error("Properties not found for new incident work product");
				}
			}

		} catch (ClassCastException e) {
			logger.error("Error casting response to CreateIncidentResponseDocument");
			incidentID = null;
		}

		return incidentID;
	}
}
