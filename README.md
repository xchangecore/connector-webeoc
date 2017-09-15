connector-webeoc
================

This connector provided the framework that can support multiple connector components to different webeoc boards.
Two boards are included in this package.  Incident Board and Position Log Board

Dependencies:
1. connector-base-util
2. connector-base-async
3. Set an environment variable called MAVEN_OPTS to -Xss4m.

To Build:
1. Build the dependencies.
2. run the install-webeoc-xmlbeans.sh(bat)
2. Run "mvn clean install" to build the webEOCAdapter.

To Run:
1. Copy the webeocAdapter/src/main/resources/contexts/webeoc-context to the same directory of the WebEOCAdapter.jar executable jar file.
2. Use an editor to open the webeoc-context file.
3. Configure the following bean properties:
	a. webEOCAdapter bean, sleepDurationInSeconds - define the duration between each polling cycle.
	b. uicdsIncidentsBoard bean, incidentName - name of the incident on WebEOC
	c. 			     boardName - name of the board on WebEOC	
        d.                           inputViewName - the input View of the board
        e.                           viewName - the display view of the board
	f. webEOCWebServiceClientConfig bean, hostURL - the webeoc server instance url api
        g.                                    user - valid user of webeoc
        h.                                    password - password of the webeoc user
        i.                                    position - one of the assigned position of the user
        j.                                    incident - name of the incident on WebEOC 
     	k. webServiceTemplate bean, default Uri - the XchangeCore host.
        l.                          credentials - valid username and password for the XchangeCore.
        m. webEOCWebServiceTemplate bean, default Uri - the webeoc server instance url api  
5. Open a cygwin or windows, change directory to where the WebEOCdapter.jar file is located, run "java -jar WebEOCAdapter.jar"
