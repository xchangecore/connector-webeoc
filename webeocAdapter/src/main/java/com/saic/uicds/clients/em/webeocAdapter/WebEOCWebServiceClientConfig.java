package com.saic.uicds.clients.em.webeocAdapter;

public class WebEOCWebServiceClientConfig {

    private String protocol;
    private String hostURL;
    private String user;
    private String password;
    private String incident;
    private String position;
    
 //   private String memamBoardName;
 //   private String memamInputViewName;
 //   private String memamViewName;
       
    public WebEOCWebServiceClientConfig() {
    }

    public String getProtocol() {

        return protocol;
    }

    public void setProtocol(String protocol) {

        this.protocol = protocol;
    }

    public String getHostURL() {

        return hostURL;
    }

    public void setHostURL(String hostURL) {

        this.hostURL = hostURL;
    }

    public String getUser() {

        return user;
    }

    public void setUser(String user) {

        this.user = user;
    }

    public String getPassword() {

        return password;
    }

    public void setPassword(String password) {

        this.password = password;
    }


    public String getIncident() {
		return incident;
	}

	public void setIncident(String incident) {
		this.incident = incident;
	}

	public String getPosition() {
		return position;
	}

	public void setPosition(String position) {
		this.position = position;
	}

	/*
	public String getMemamBoardName() {
		return memamBoardName;
	}

	public void setMemamBoardName(String memamBoardName) {
		this.memamBoardName = memamBoardName;
	}

	public String getMemamInputViewName() {
		return memamInputViewName;
	}

	public void setMemamInputViewName(String memamInputViewName) {
		this.memamInputViewName = memamInputViewName;
	}

	public String getMemamViewName() {
		return memamViewName;
	}

	public void setMemamViewName(String memamViewName) {
		this.memamViewName = memamViewName;
	}
	*/
	
}
